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

import com.ntsocial.meshlink.core.common.util.safeCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

interface RadioFleetManager {
    val snapshots: StateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>>

    val rememberedProfiles: StateFlow<List<RadioEndpointProfile>>

    val selectedEndpointId: StateFlow<RadioEndpointId?>

    suspend fun start(legacyAddress: String?, legacyName: String?)

    suspend fun stop()

    suspend fun register(candidate: DiscoveredRadio, connect: Boolean = true): RadioEndpointProfile

    suspend fun select(endpointId: RadioEndpointId)

    suspend fun connect(endpointId: RadioEndpointId, preparation: RadioConnectionPreparation? = null)

    /** Internal pairing/retry: retains the connection lease and desired connection. */
    suspend fun disconnect(endpointId: RadioEndpointId)

    /** Explicit user intent: persists disconnect, tears down only this runtime, then releases its slot. */
    suspend fun disconnectAndRelease(endpointId: RadioEndpointId)

    suspend fun setLegacyPrimary(endpointId: RadioEndpointId)

    suspend fun remove(endpointId: RadioEndpointId)

    fun requireCurrentGeneration(endpointId: RadioEndpointId, expectedGeneration: Long)
}

/** Fleet slots represent runtime leases, not remembered devices or the compatibility-primary role. */
@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class DefaultRadioFleetManager(
    private val endpointStore: RadioEndpointStore,
    private val sessionFactory: RadioEndpointSessionFactory,
    private val scope: CoroutineScope,
) : RadioFleetManager {
    private class Lease(val runtimeId: String = Uuid.random().toString()) {
        val operationMutex = Mutex()
        var session: RadioEndpointSession? = null
        var observer: Job? = null
        var connecting: Job? = null
        var releaseFailure: Throwable? = null
    }

    private val lifecycleMutex = Mutex()
    private val leases = mutableMapOf<RadioEndpointId, Lease>()
    private val _snapshots = MutableStateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>>(emptyMap())
    override val snapshots: StateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>> = _snapshots.asStateFlow()
    override val selectedEndpointId: StateFlow<RadioEndpointId?> = endpointStore.selectedEndpointId
    override val rememberedProfiles: StateFlow<List<RadioEndpointProfile>> = endpointStore.profiles

    override suspend fun start(legacyAddress: String?, legacyName: String?) {
        endpointStore.migrateLegacySelection(legacyAddress, legacyName)
        // Start independently: an unreachable radio's READY timeout must not block the other three.
        endpointStore.profiles.value
            .filter { it.enabled && it.autoConnect }
            .forEach { profile -> scope.launch { connectOwned(profile.id, restoreOnly = true) } }
    }

    override suspend fun stop() {
        val current = lifecycleMutex.withLock { leases.toMap() }
        current.forEach { (endpointId, lease) -> closeLease(endpointId, lease) }
    }

    override suspend fun register(candidate: DiscoveredRadio, connect: Boolean): RadioEndpointProfile {
        val remembered = endpointStore.register(candidate)
        val profile = lifecycleMutex.withLock { acquireLocked(remembered.id).first }
        endpointStore.select(profile.id)
        if (connect) connect(profile.id)
        return profile
    }

    override suspend fun select(endpointId: RadioEndpointId) {
        require(snapshots.value[endpointId]?.profile?.enabled == true) { "Endpoint has no active connection lease" }
        endpointStore.select(endpointId)
    }

    override suspend fun connect(endpointId: RadioEndpointId, preparation: RadioConnectionPreparation?) {
        connectOwned(endpointId, preparation)
    }

    private suspend fun connectOwned(
        endpointId: RadioEndpointId,
        preparation: RadioConnectionPreparation? = null,
        restoreOnly: Boolean = false,
    ) {
        val (profile, lease) =
            lifecycleMutex.withLock {
                if (
                    restoreOnly &&
                    endpointStore.profiles.value.none { it.id == endpointId && it.enabled && it.autoConnect }
                ) {
                    return
                }
                if (preparation != null) {
                    check(leases[endpointId]?.runtimeId == preparation.runtimeId) { "Pairing connection was released" }
                }
                acquireLocked(endpointId)
            }
        val job =
            lease.operationMutex.withLock {
                val snapshot = snapshots.value[endpointId]
                if (snapshot?.runtimeId != lease.runtimeId || !snapshot.profile.enabled) return@withLock null
                lease.connecting?.takeIf { it.isActive }
                    ?: scope
                        .launch(start = CoroutineStart.LAZY) { startLease(profile, lease, preparation) }
                        .also { lease.connecting = it }
            }
        job?.start()
        job?.join()
    }

    private suspend fun startLease(
        profile: RadioEndpointProfile,
        lease: Lease,
        preparation: RadioConnectionPreparation?,
    ) {
        try {
            val session =
                lease.session
                    ?: sessionFactory.create(profile).also {
                        lease.session = it
                        observeSession(profile, lease, it)
                    }
            try {
                if (preparation != null) {
                    session.stop()
                    preparation.prepare()
                }
                session.start()
            } finally {
                withContext(NonCancellable) { preparation?.close() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateLease(profile.id, lease) { it.copy(state = EndpointSessionState.Failed(reason = error.message)) }
        }
    }

    override suspend fun disconnect(endpointId: RadioEndpointId) {
        val lease = lifecycleMutex.withLock { leases[endpointId] } ?: return
        lease.operationMutex.withLock {
            lease.connecting?.cancelAndJoin()
            lease.connecting = null
            lease.session?.stop()
        }
    }

    override suspend fun disconnectAndRelease(endpointId: RadioEndpointId) = withContext(NonCancellable) {
        // Persisting disabled intent can dispose the initiating Connections entry. Finish the admitted release even
        // when that entry's coroutine is cancelled before the store publishes its committed profile update.
        val lease =
            lifecycleMutex.withLock {
                val profile = endpointStore.profiles.value.firstOrNull { it.id == endpointId } ?: return@withContext
                // Persist before asynchronous teardown: process death or restoration cannot resurrect this intent.
                val disconnected = profile.copy(enabled = false, autoConnect = false)
                endpointStore.update(disconnected)
                _snapshots.update { current ->
                    current[endpointId]?.let { snapshot ->
                        current +
                            (
                                endpointId to
                                    snapshot.copy(profile = disconnected, state = EndpointSessionState.Releasing)
                                )
                    } ?: current
                }
                leases[endpointId]
            } ?: return@withContext
        closeLease(endpointId, lease)
    }

    override suspend fun setLegacyPrimary(endpointId: RadioEndpointId) {
        require(endpointStore.profiles.value.singleOrNull { it.legacyPrimary }?.id == endpointId) {
            "Compatibility-primary reassignment requires an explicit Gateway migration"
        }
    }

    override suspend fun remove(endpointId: RadioEndpointId) = withContext(NonCancellable) {
        require(endpointStore.profiles.value.none { it.id == endpointId && it.legacyPrimary }) {
            "Disconnect the compatibility endpoint to release its slot; its binding is retained"
        }
        disconnectAndRelease(endpointId)
        check(endpointId !in snapshots.value) { "Endpoint disconnect is not complete" }
        endpointStore.remove(endpointId)
    }

    override fun requireCurrentGeneration(endpointId: RadioEndpointId, expectedGeneration: Long) {
        val snapshot = snapshots.value[endpointId]
        val actual = snapshot?.generation ?: 0L
        if (snapshot?.profile?.enabled != true || actual != expectedGeneration) {
            throw StaleRadioEndpointGenerationException(endpointId, expectedGeneration, actual)
        }
    }

    private suspend fun acquireLocked(endpointId: RadioEndpointId): Pair<RadioEndpointProfile, Lease> {
        val profile = requireNotNull(endpointStore.profiles.value.firstOrNull { it.id == endpointId })
        leases[endpointId]?.let { lease ->
            check(snapshots.value[endpointId]?.profile?.enabled == true) { "Endpoint disconnect is not complete" }
            return profile to lease
        }
        if (leases.size >= MAX_RADIO_ENDPOINTS) throw RadioEndpointLimitExceededException()
        val active = profile.copy(enabled = true, autoConnect = true)
        endpointStore.update(active)
        val lease = Lease()
        leases[endpointId] = lease
        _snapshots.update { it + (endpointId to RadioEndpointSnapshot(active, runtimeId = lease.runtimeId)) }
        return active to lease
    }

    private fun observeSession(profile: RadioEndpointProfile, lease: Lease, session: RadioEndpointSession) {
        lease.observer =
            scope.launch {
                combine(session.state, session.generation) { state, generation -> state to generation }
                    .collect { (state, generation) ->
                        updateLease(profile.id, lease) { it.copy(state = state, generation = generation) }
                    }
            }
    }

    private fun updateLease(
        endpointId: RadioEndpointId,
        lease: Lease,
        transform: (RadioEndpointSnapshot) -> RadioEndpointSnapshot,
    ) {
        _snapshots.update { current ->
            val snapshot = current[endpointId]
            if (snapshot?.runtimeId == lease.runtimeId && snapshot.profile.enabled) {
                current + (endpointId to transform(snapshot))
            } else {
                current
            }
        }
    }

    private suspend fun closeLease(endpointId: RadioEndpointId, lease: Lease) {
        lease.operationMutex.withLock {
            val completed =
                safeCatching {
                    withTimeoutOrNull(RELEASE_TIMEOUT) {
                        lease.session?.revoke()
                        lease.connecting?.cancelAndJoin()
                        lease.connecting = null
                        lease.observer?.cancelAndJoin()
                        lease.session?.close()
                        true
                    } == true
                }
                    .getOrElse { error ->
                        lease.releaseFailure = error
                        false
                    }
            lifecycleMutex.withLock {
                if (leases[endpointId] !== lease) return@withLock
                if (completed) {
                    leases.remove(endpointId)
                    _snapshots.update { it - endpointId }
                } else {
                    // Keep the slot reserved until transport teardown has actually completed; a retry is safe.
                    _snapshots.update { current ->
                        current[endpointId]?.let { snapshot ->
                            current +
                                (
                                    endpointId to
                                        snapshot.copy(
                                            state = EndpointSessionState.Failed(reason = "disconnect_incomplete"),
                                        )
                                    )
                        } ?: current
                    }
                }
            }
        }
    }
}

private val RELEASE_TIMEOUT = 15.seconds
