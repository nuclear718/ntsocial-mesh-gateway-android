/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.ntsocial.meshlink.core.radiofleet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RadioFleetManager {
    val snapshots: StateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>>

    val selectedEndpointId: StateFlow<RadioEndpointId?>

    suspend fun start(legacyAddress: String?, legacyName: String?)

    suspend fun stop()

    suspend fun register(candidate: DiscoveredRadio, connect: Boolean = true): RadioEndpointProfile

    suspend fun select(endpointId: RadioEndpointId)

    suspend fun connect(endpointId: RadioEndpointId)

    suspend fun disconnect(endpointId: RadioEndpointId)

    suspend fun setLegacyPrimary(endpointId: RadioEndpointId)

    suspend fun remove(endpointId: RadioEndpointId)

    fun requireCurrentGeneration(endpointId: RadioEndpointId, expectedGeneration: Long)
}

/** Fleet orchestration boundary: endpoint failures are projected as state instead of escaping the service scope. */
@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class DefaultRadioFleetManager(
    private val endpointStore: RadioEndpointStore,
    private val sessionFactory: RadioEndpointSessionFactory,
    private val scope: CoroutineScope,
) : RadioFleetManager {
    private val lifecycleMutex = Mutex()
    private val bootstrapMutex = Mutex()
    private val sessions = mutableMapOf<RadioEndpointId, RadioEndpointSession>()
    private val sessionJobs = mutableMapOf<RadioEndpointId, Job>()
    private var profilesJob: Job? = null

    private val _snapshots = MutableStateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>>(emptyMap())
    override val snapshots: StateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>> = _snapshots.asStateFlow()
    override val selectedEndpointId: StateFlow<RadioEndpointId?> = endpointStore.selectedEndpointId

    override suspend fun start(legacyAddress: String?, legacyName: String?) {
        endpointStore.migrateLegacySelection(legacyAddress, legacyName)
        lifecycleMutex.withLock {
            if (profilesJob == null) {
                profilesJob =
                    scope.launch { endpointStore.profiles.collectLatest { profiles -> reconcileProfiles(profiles) } }
            }
        }
        reconcileProfiles(endpointStore.profiles.value)
        endpointStore.profiles.value
            .filter { it.enabled && it.autoConnect }
            .sortedWith(compareByDescending<RadioEndpointProfile> { it.legacyPrimary }.thenByDescending { it.priority })
            .forEach { profile -> connectInternal(profile.id) }
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        profilesJob?.cancelAndJoin()
        profilesJob = null
        sessionJobs.values.forEach { it.cancel() }
        sessionJobs.clear()
        sessions.values.forEach { it.close() }
        sessions.clear()
        _snapshots.value = emptyMap()
    }

    override suspend fun register(candidate: DiscoveredRadio, connect: Boolean): RadioEndpointProfile {
        val profile = endpointStore.register(candidate)
        var creationFailure: Exception? = null
        lifecycleMutex.withLock {
            try {
                ensureSessionLocked(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                creationFailure = error
            }
            _snapshots.update { current ->
                current +
                    (
                        profile.id to
                            (
                                current[profile.id]?.copy(
                                    profile = profile,
                                    state =
                                    creationFailure?.let { EndpointSessionState.Failed(reason = it.message) }
                                        ?: current[profile.id]?.state
                                        ?: EndpointSessionState.Registered,
                                )
                                    ?: RadioEndpointSnapshot(
                                        profile = profile,
                                        state =
                                        creationFailure?.let { EndpointSessionState.Failed(reason = it.message) }
                                            ?: EndpointSessionState.Registered,
                                    )
                                )
                        )
            }
        }
        endpointStore.select(profile.id)
        if (connect && profile.enabled && creationFailure == null) connectInternal(profile.id)
        return profile
    }

    override suspend fun select(endpointId: RadioEndpointId) {
        require(endpointId in endpointStore.profiles.value.map { it.id }) { "Unknown endpoint $endpointId" }
        endpointStore.select(endpointId)
    }

    override suspend fun connect(endpointId: RadioEndpointId) {
        require(endpointId in endpointStore.profiles.value.map { it.id }) { "Unknown endpoint $endpointId" }
        connectInternal(endpointId)
    }

    override suspend fun disconnect(endpointId: RadioEndpointId) {
        val session = lifecycleMutex.withLock { sessions[endpointId] }
        session?.stop()
    }

    override suspend fun setLegacyPrimary(endpointId: RadioEndpointId) {
        val currentPrimary = endpointStore.profiles.value.singleOrNull { it.legacyPrimary }
        require(currentPrimary?.id == endpointId) {
            "Legacy-primary reassignment is not supported by the phase-one Android Gateway projection"
        }
        endpointStore.setLegacyPrimary(endpointId)
    }

    override suspend fun remove(endpointId: RadioEndpointId) = lifecycleMutex.withLock {
        val profile = endpointStore.profiles.value.firstOrNull { it.id == endpointId }
        require(profile?.legacyPrimary != true) {
            "The legacy-primary endpoint cannot be removed while the Android Gateway projects it"
        }
        sessionJobs.remove(endpointId)?.cancelAndJoin()
        sessions.remove(endpointId)?.close()
        endpointStore.remove(endpointId)
        _snapshots.update { it - endpointId }
    }

    override fun requireCurrentGeneration(endpointId: RadioEndpointId, expectedGeneration: Long) {
        val actual = snapshots.value[endpointId]?.generation ?: 0L
        if (actual != expectedGeneration) {
            throw StaleRadioEndpointGenerationException(endpointId, expectedGeneration, actual)
        }
    }

    private suspend fun reconcileProfiles(profiles: List<RadioEndpointProfile>) = lifecycleMutex.withLock {
        val creationFailures = mutableMapOf<RadioEndpointId, Exception>()
        val profileIds = profiles.mapTo(mutableSetOf()) { it.id }
        (sessions.keys - profileIds).forEach { endpointId ->
            sessionJobs.remove(endpointId)?.cancelAndJoin()
            sessions.remove(endpointId)?.close()
        }
        profiles.forEach { profile ->
            val previousProfile = _snapshots.value[profile.id]?.profile
            if (previousProfile != null && previousProfile.legacyPrimary != profile.legacyPrimary) {
                sessionJobs.remove(profile.id)?.cancelAndJoin()
                sessions.remove(profile.id)?.close()
            }
            try {
                ensureSessionLocked(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                creationFailures[profile.id] = error
            }
        }
        _snapshots.update { current ->
            profiles.associate { profile ->
                val previous = current[profile.id]
                profile.id to
                    (
                        previous?.copy(
                            profile = profile,
                            state =
                            creationFailures[profile.id]?.let { EndpointSessionState.Failed(reason = it.message) }
                                ?: previous.state,
                        )
                            ?: RadioEndpointSnapshot(
                                profile = profile,
                                state =
                                creationFailures[profile.id]?.let {
                                    EndpointSessionState.Failed(reason = it.message)
                                } ?: EndpointSessionState.Registered,
                            )
                        )
            }
        }
    }

    private suspend fun ensureSessionLocked(profile: RadioEndpointProfile): RadioEndpointSession {
        sessions[profile.id]?.let {
            return it
        }
        val session = sessionFactory.create(profile)
        sessions[profile.id] = session
        sessionJobs[profile.id] =
            scope.launch {
                combine(session.state, session.generation) { state, generation -> state to generation }
                    .collectLatest { (state, generation) ->
                        _snapshots.update { current ->
                            val currentSnapshot = current[profile.id] ?: RadioEndpointSnapshot(profile)
                            current + (profile.id to currentSnapshot.copy(state = state, generation = generation))
                        }
                    }
            }
        return session
    }

    private suspend fun connectInternal(endpointId: RadioEndpointId) = bootstrapMutex.withLock {
        val profile = endpointStore.profiles.value.firstOrNull { it.id == endpointId } ?: return@withLock
        var generation = 0L
        try {
            val session = lifecycleMutex.withLock { ensureSessionLocked(profile) }
            generation = session.generation.value
            session.start()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _snapshots.update { current ->
                val snapshot = current[endpointId] ?: RadioEndpointSnapshot(profile)
                current +
                    (
                        endpointId to
                            snapshot.copy(
                                state = EndpointSessionState.Failed(reason = error.message),
                                generation = generation,
                            )
                        )
            }
        }
    }
}
