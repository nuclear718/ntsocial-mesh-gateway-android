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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

        assertTrue(factory.sessions.isEmpty())
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

    @Test
    fun `primary release frees slot without promoting or restarting another endpoint and survives restart`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)
        val profiles =
            (1..4).map {
                manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:0$it", displayName = "Radio $it"))
            }
        val primary = profiles.first()
        val oldRuntime = manager.snapshots.value.getValue(primary.id).runtimeId
        assertFailsWith<RadioEndpointLimitExceededException> {
            manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:05", displayName = "Fifth"))
        }
        manager.disconnectAndRelease(primary.id)
        assertEquals(3, manager.snapshots.value.size)
        assertFalse(store.profiles.value.first { it.id == primary.id }.enabled)
        assertFalse(store.profiles.value.first { it.id == primary.id }.autoConnect)
        assertEquals(primary.id, store.profiles.value.single { it.legacyPrimary }.id)
        profiles.drop(1).forEach { assertEquals(0, factory.sessions.getValue(it.id).closeCount) }
        val fifth = manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:05", displayName = "Fifth"))
        assertEquals(4, manager.snapshots.value.size)
        manager.stop()
        val restoredFactory = FakeSessionFactory()
        val restored = DefaultRadioFleetManager(store, restoredFactory, this)
        restored.start(null, null)
        runCurrent()
        assertEquals(4, restored.snapshots.value.size)
        assertFalse(primary.id in restoredFactory.sessions)
        restored.disconnectAndRelease(fifth.id)
        val reconnected = restored.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "First again"))
        assertEquals(primary.id, reconnected.id)
        assertNotEquals(oldRuntime, restored.snapshots.value.getValue(primary.id).runtimeId)
        restored.stop()
    }

    @Test
    fun `disconnect is durable before teardown and slot remains reserved until close completes`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)
        val profiles =
            (1..4).map {
                manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:0$it", displayName = "Radio $it"))
            }
        val primary = profiles.first()
        val barrier = CompletableDeferred<Unit>()
        factory.sessions.getValue(primary.id).closeBarrier = barrier
        val releasing = launch { manager.disconnectAndRelease(primary.id) }
        runCurrent()
        assertFalse(store.profiles.value.first { it.id == primary.id }.autoConnect)
        assertIs<EndpointSessionState.Releasing>(manager.snapshots.value.getValue(primary.id).state)
        assertEquals(1, factory.sessions.getValue(primary.id).revokeCount)
        assertFailsWith<RadioEndpointLimitExceededException> {
            manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:05", displayName = "Fifth"))
        }
        barrier.complete(Unit)
        releasing.join()
        manager.disconnectAndRelease(primary.id)
        assertEquals(1, factory.sessions.getValue(primary.id).closeCount)
        manager.stop()
    }

    @Test
    fun `disposing connections after disabled persistence still completes release`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)
        val profile = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "Primary"))
        val persisted = CompletableDeferred<Unit>()
        val finishUpdate = CompletableDeferred<Unit>()
        store.afterDisabledUpdate = {
            persisted.complete(Unit)
            finishUpdate.await()
        }

        val releasing = launch { manager.disconnectAndRelease(profile.id) }
        persisted.await()
        assertFalse(store.profiles.value.single().enabled)
        assertEquals(null, store.selectedEndpointId.value)
        assertTrue(profile.id in manager.snapshots.value)
        releasing.cancel()
        finishUpdate.complete(Unit)
        releasing.join()

        assertFalse(profile.id in manager.snapshots.value)
        assertEquals(1, factory.sessions.getValue(profile.id).revokeCount)
        assertEquals(1, factory.sessions.getValue(profile.id).closeCount)
        assertEquals(profile.id, store.profiles.value.single().id)
        assertFalse(store.profiles.value.single().autoConnect)
        manager.stop()
    }

    @Test
    fun `disposing selected secondary during removal still removes its remembered profile`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)
        val primary = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "Primary"))
        val secondary =
            manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:02", displayName = "Secondary"))
        val removalStarted = CompletableDeferred<Unit>()
        val finishRemoval = CompletableDeferred<Unit>()
        store.beforeRemove = {
            removalStarted.complete(Unit)
            finishRemoval.await()
        }

        val removing = launch { manager.remove(secondary.id) }
        removalStarted.await()
        assertFalse(secondary.id in manager.snapshots.value)
        removing.cancel()
        finishRemoval.complete(Unit)
        removing.join()

        assertEquals(listOf(primary.id), store.profiles.value.map { it.id })
        assertEquals(setOf(primary.id), manager.snapshots.value.keys)
        assertEquals(1, factory.sessions.getValue(secondary.id).closeCount)
        assertEquals(0, factory.sessions.getValue(primary.id).closeCount)
        manager.stop()
    }

    @Test
    fun `unreachable startup does not block a second radio and release cancels pending startup`() = runTest {
        val store = FakeEndpointStore()
        val blocked = CompletableDeferred<Unit>()
        val sessions = mutableMapOf<RadioEndpointId, FakeSession>()
        val factory = RadioEndpointSessionFactory { profile ->
            FakeSession(profile.id).also {
                if (profile.legacyPrimary) it.startBarrier = blocked
                sessions[profile.id] = it
            }
        }
        val manager = DefaultRadioFleetManager(store, factory, this)
        val primary = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "A"), connect = false)
        val first = launch { manager.connect(primary.id) }
        runCurrent()
        val second = manager.register(DiscoveredRadio(transportAddress = "xAA:BB:CC:DD:EE:02", displayName = "B"))
        assertEquals(1, sessions.getValue(second.id).startCount)
        manager.disconnectAndRelease(primary.id)
        first.join()
        assertFalse(primary.id in manager.snapshots.value)
        assertEquals(0, sessions.getValue(second.id).closeCount)
        manager.stop()
    }

    @Test
    fun `release cancels native pairing and waits for peripheral cleanup before freeing quota`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)
        val primary = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "A"), connect = false)
        val runtime = manager.snapshots.value.getValue(primary.id).runtimeId
        val preparing = CompletableDeferred<Unit>()
        val cleanup = CompletableDeferred<Unit>()
        val request =
            object : RadioConnectionPreparation {
                override val runtimeId = runtime

                override suspend fun prepare() {
                    preparing.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }

                override suspend fun close() {
                    cleanup.await()
                }
            }
        val connecting = launch { manager.connect(primary.id, request) }
        preparing.await()
        val release = launch { manager.disconnectAndRelease(primary.id) }
        runCurrent()
        assertTrue(primary.id in manager.snapshots.value)
        assertEquals(0, factory.sessions.getValue(primary.id).startCount)
        cleanup.complete(Unit)
        release.join()
        connecting.join()
        assertFalse(primary.id in manager.snapshots.value)
        assertFailsWith<IllegalStateException> { manager.connect(primary.id, request) }
        assertFalse(primary.id in manager.snapshots.value)
        manager.stop()
    }

    @Test
    fun `queued process restoration cannot revive a later explicit disconnect`() = runTest {
        val store = FakeEndpointStore()
        val factory = FakeSessionFactory()
        val manager = DefaultRadioFleetManager(store, factory, this)
        val primary = manager.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "A"), connect = false)
        manager.stop()
        manager.start(null, null)
        manager.disconnectAndRelease(primary.id)
        runCurrent()
        assertTrue(manager.snapshots.value.isEmpty())
        assertFalse(store.profiles.value.single().autoConnect)
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
        var closeCount = 0
        var revokeCount = 0

        override fun revoke() {
            revokeCount++
        }

        var startBarrier: CompletableDeferred<Unit>? = null
        var closeBarrier: CompletableDeferred<Unit>? = null
        var startCount: Int = 0
            private set

        override suspend fun start() {
            startCount += 1
            mutableState.value = EndpointSessionState.Connecting
            startBarrier?.await()
        }

        override suspend fun stop() {
            mutableState.value = EndpointSessionState.Registered
        }

        override suspend fun close() {
            closeBarrier?.await()
            closeCount++
            stop()
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
        var afterDisabledUpdate: (suspend () -> Unit)? = null
        var beforeRemove: (suspend () -> Unit)? = null

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
            if (mutableProfiles.value.none { it.enabled && it.id == mutableSelected.value }) {
                mutableSelected.value = mutableProfiles.value.firstOrNull { it.enabled }?.id
            }
            if (!profile.enabled) afterDisabledUpdate?.invoke()
        }

        override suspend fun select(endpointId: RadioEndpointId) {
            mutableSelected.value = endpointId
        }

        override suspend fun setLegacyPrimary(endpointId: RadioEndpointId) {
            mutableProfiles.value = mutableProfiles.value.map { it.copy(legacyPrimary = it.id == endpointId) }
        }

        override suspend fun remove(endpointId: RadioEndpointId) {
            beforeRemove?.invoke()
            mutableProfiles.value = mutableProfiles.value.filterNot { it.id == endpointId }
            if (mutableSelected.value == endpointId) mutableSelected.value = mutableProfiles.value.firstOrNull()?.id
        }

        private fun String.normalized(): String = uppercase().replace(":", "")
    }

    private companion object {
        const val ADDRESS = "xAA:BB:CC:DD:EE:01"
    }
}
