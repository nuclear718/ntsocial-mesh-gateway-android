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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DefaultRadioFleetManagerTest {
    @Test
    fun `four endpoint sessions retain independent state and generation`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)

        manager.start(null, null)
        val profiles =
            (1..MAX_RADIO_ENDPOINTS).map { index ->
                manager.register(
                    DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:0$index", displayName = "Radio $index"),
                )
            }
        advanceUntilIdle()

        profiles.forEachIndexed { index, profile ->
            factory.sessions.getValue(profile.id).publishReady(generation = (index + 11).toLong())
        }
        advanceUntilIdle()

        assertEquals(4, manager.snapshots.value.size)
        assertEquals(setOf(11L, 12L, 13L, 14L), manager.snapshots.value.values.map { it.generation }.toSet())
        profiles.forEachIndexed { index, profile ->
            manager.requireCurrentGeneration(profile.id, expectedGeneration = (index + 11).toLong())
        }
        assertFailsWith<StaleRadioEndpointGenerationException> {
            manager.requireCurrentGeneration(profiles.first().id, expectedGeneration = 99L)
        }
        manager.stop()
    }

    @Test
    fun `registering the same address reuses one session`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)

        val first = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "First"))
        val second = manager.register(DiscoveredRadio(transportAddress = ADDRESS.lowercase(), displayName = "Second"))
        advanceUntilIdle()

        assertEquals(first.id, second.id)
        assertEquals(1, factory.sessions.size)
        assertEquals(2, factory.sessions.getValue(first.id).startCount)
        manager.stop()
    }

    @Test
    fun `phase one prevents legacy primary reassignment or removal`() = runTest {
        val manager = DefaultRadioFleetManager(FakeEndpointStore(), FakeSessionFactory(), this)
        val primary = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "Primary"))
        val secondary =
            manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:02", displayName = "Secondary"))

        assertFailsWith<IllegalArgumentException> { manager.setLegacyPrimary(secondary.id) }
        assertFailsWith<IllegalArgumentException> { manager.remove(primary.id) }
        manager.remove(secondary.id)
        assertEquals(setOf(primary.id), manager.snapshots.value.keys)
        manager.stop()
    }

    @Test
    fun `session creation failure is projected without dropping the endpoint`() = runTest {
        val manager =
            DefaultRadioFleetManager(
                endpointStore = FakeEndpointStore(),
                sessionFactory = FakeSessionFactory(createFailure = IllegalStateException("radio unavailable")),
                scope = this,
            )

        val profile = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "Unavailable"))

        val failed = assertIs<EndpointSessionState.Failed>(manager.snapshots.value.getValue(profile.id).state)
        assertEquals("radio unavailable", failed.reason)
        manager.stop()
    }

    @Test
    fun `registration can defer transport start until endpoint role is projected`() = runTest {
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(FakeEndpointStore(), factory, this)

        val profile =
            manager.register(
                candidate = DiscoveredRadio(transportAddress = ADDRESS, displayName = "Deferred"),
                connect = false,
            )

        assertEquals(0, factory.sessions.getValue(profile.id).startCount)
        manager.connect(profile.id)
        assertEquals(1, factory.sessions.getValue(profile.id).startCount)
        manager.stop()
    }

    @Test
    fun `generation-only changes update the fleet snapshot`() = runTest {
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(FakeEndpointStore(), factory, this)
        val profile = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "Generation"))
        advanceUntilIdle()

        factory.sessions.getValue(profile.id).publishGeneration(27L)
        advanceUntilIdle()

        assertEquals(27L, manager.snapshots.value.getValue(profile.id).generation)
        manager.requireCurrentGeneration(profile.id, expectedGeneration = 27L)
        manager.stop()
    }

    private class FakeSessionFactory(private val createFailure: Exception? = null) : RadioEndpointSessionFactory {
        val sessions = mutableMapOf<RadioEndpointId, FakeSession>()

        override suspend fun create(profile: RadioEndpointProfile): RadioEndpointSession {
            createFailure?.let { throw it }
            return FakeSession(profile.id).also { sessions[profile.id] = it }
        }
    }

    private class FakeSession(override val endpointId: RadioEndpointId) : RadioEndpointSession {
        private val mutableState = MutableStateFlow<EndpointSessionState>(EndpointSessionState.Registered)
        override val state: StateFlow<EndpointSessionState> = mutableState
        private val mutableGeneration = MutableStateFlow(0L)
        override val generation: StateFlow<Long> = mutableGeneration
        var startCount: Int = 0
            private set

        override suspend fun start() {
            startCount += 1
            mutableState.value = EndpointSessionState.Connecting
        }

        override suspend fun stop() {
            mutableState.value = EndpointSessionState.Registered
        }

        fun publishReady(generation: Long) {
            mutableGeneration.value = generation
            mutableState.value = EndpointSessionState.Ready(generation)
        }

        fun publishGeneration(generation: Long) {
            mutableGeneration.value = generation
        }
    }

    private class FakeEndpointStore : RadioEndpointStore {
        private val mutableProfiles = MutableStateFlow<List<RadioEndpointProfile>>(emptyList())
        override val profiles: StateFlow<List<RadioEndpointProfile>> = mutableProfiles
        private val mutableSelected = MutableStateFlow<RadioEndpointId?>(null)
        override val selectedEndpointId: StateFlow<RadioEndpointId?> = mutableSelected
        private var nextId = 1

        override suspend fun migrateLegacySelection(address: String?, name: String?): RadioEndpointProfile? = null

        override suspend fun register(candidate: DiscoveredRadio): RadioEndpointProfile {
            val existing =
                mutableProfiles.value.firstOrNull {
                    it.protocol == candidate.protocol && it.normalizedAddress == candidate.transportAddress.normalized()
                }
            if (existing != null) {
                val updated = existing.copy(displayName = candidate.displayName)
                mutableProfiles.value = mutableProfiles.value.map { if (it.id == updated.id) updated else it }
                mutableSelected.value = updated.id
                return updated
            }
            if (mutableProfiles.value.size >= MAX_RADIO_ENDPOINTS) throw RadioEndpointLimitExceededException()
            val created =
                RadioEndpointProfile(
                    id = RadioEndpointId("endpoint-${nextId++}"),
                    protocol = candidate.protocol,
                    transportAddress = candidate.transportAddress,
                    displayName = candidate.displayName,
                    legacyPrimary = mutableProfiles.value.isEmpty(),
                )
            mutableProfiles.value += created
            mutableSelected.value = created.id
            return created
        }

        override suspend fun update(profile: RadioEndpointProfile) {
            mutableProfiles.value = mutableProfiles.value.map { if (it.id == profile.id) profile else it }
        }

        override suspend fun select(endpointId: RadioEndpointId) {
            mutableSelected.value = endpointId
        }

        override suspend fun setLegacyPrimary(endpointId: RadioEndpointId) {
            mutableProfiles.value = mutableProfiles.value.map { it.copy(legacyPrimary = it.id == endpointId) }
        }

        override suspend fun remove(endpointId: RadioEndpointId) {
            mutableProfiles.value = mutableProfiles.value.filterNot { it.id == endpointId }
            if (mutableSelected.value == endpointId) mutableSelected.value = mutableProfiles.value.firstOrNull()?.id
        }

        private fun String.normalized(): String = uppercase().replace(":", "")
    }

    private companion object {
        const val ADDRESS = "xAA:BB:CC:DD:EE:01"
    }
}
