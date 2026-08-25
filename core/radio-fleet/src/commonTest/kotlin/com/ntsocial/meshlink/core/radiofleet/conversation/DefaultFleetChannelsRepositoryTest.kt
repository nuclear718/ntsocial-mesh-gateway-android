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
package com.ntsocial.meshlink.core.radiofleet.conversation

import com.ntsocial.meshlink.core.radiofleet.DiscoveredRadio
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultFleetChannelsRepositoryTest {
    @Test
    fun `same local channel from four endpoints remains distinct`() = runTest {
        val profiles = (0 until 4).map(::profile)
        val fleetManager = FakeFleetManager(profiles)
        val registry = FakeSourceRegistry(profiles.associate { it.id to registeredSource(it.id) })
        val repositoryScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repository =
            DefaultFleetChannelsRepository(
                fleetManager = fleetManager,
                sourceRegistry = registry,
                appearanceStore = FakeAppearanceStore(profiles),
                scope = repositoryScope,
            )
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { repository.groups.collect {} }

        advanceUntilIdle()

        assertEquals(4, repository.groups.value.size)
        val keys = repository.groups.value.flatMap { it.channels }.map { it.key }.toSet()
        assertEquals(4, keys.size)
        assertEquals(setOf("meshtastic:0"), keys.map { it.localChannelId.value }.toSet())
        collector.cancel()
        repositoryScope.cancel()
    }

    @Test
    fun `missing source keeps endpoint group visible and read only`() = runTest {
        val profile = profile(0)
        val fleetManager = FakeFleetManager(listOf(profile))
        val repositoryScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repository =
            DefaultFleetChannelsRepository(
                fleetManager = fleetManager,
                sourceRegistry = FakeSourceRegistry(emptyMap()),
                appearanceStore = FakeAppearanceStore(listOf(profile)),
                scope = repositoryScope,
            )
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { repository.groups.collect {} }

        advanceUntilIdle()

        val group = repository.groups.value.single()
        assertFalse(group.dataAvailable)
        assertTrue(group.channels.isEmpty())
        collector.cancel()
        repositoryScope.cancel()
    }

    private fun profile(index: Int) = RadioEndpointProfile(
        id = RadioEndpointId("endpoint-$index"),
        protocol = RadioProtocol.MESHTASTIC,
        transportAddress = "xAA:BB:CC:DD:EE:0$index",
        displayName = "Node $index",
        legacyPrimary = index == 0,
    )

    private fun registeredSource(endpointId: RadioEndpointId): RegisteredEndpointConversationSource {
        val channel =
            FleetChannelSummary(
                key = FleetChannelKey(endpointId, LocalChannelId("meshtastic:0")),
                localContactKey = "0^all",
                channelIndex = 0,
                name = "LongFast",
                role = FleetChannelRole.PRIMARY,
                security = ChannelSecurityKind.WELL_KNOWN,
                unreadCount = 0,
                lastMessageText = null,
                lastMessageAtMillis = null,
                isMuted = false,
            )
        val source =
            object : EndpointConversationSource {
                override val endpointId = endpointId
                override val snapshot =
                    MutableStateFlow(
                        EndpointConversationSnapshot(
                            endpointId = endpointId,
                            channels = listOf(channel),
                            hasCachedCatalog = true,
                        ),
                    )
            }
        return RegisteredEndpointConversationSource("runtime-${endpointId.value}", source)
    }

    private class FakeSourceRegistry(initial: Map<RadioEndpointId, RegisteredEndpointConversationSource>) :
        EndpointConversationSourceRegistry {
        override val entries = MutableStateFlow(initial)
    }

    private class FakeAppearanceStore(profiles: List<RadioEndpointProfile>) : EndpointAppearanceStore {
        override val appearances =
            MutableStateFlow(
                profiles.mapIndexed { index, profile -> profile.id to defaultEndpointAppearance(index) }.toMap(),
            )

        override suspend fun reconcile(profiles: List<RadioEndpointProfile>) = Unit

        override suspend fun update(endpointId: RadioEndpointId, appearance: EndpointAppearance) {
            appearances.value += endpointId to appearance
        }

        override suspend fun remove(endpointId: RadioEndpointId) {
            appearances.value -= endpointId
        }
    }

    private class FakeFleetManager(profiles: List<RadioEndpointProfile>) : RadioFleetManager {
        override val snapshots: StateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>> =
            MutableStateFlow(
                profiles.associate { profile ->
                    profile.id to
                        RadioEndpointSnapshot(
                            profile = profile,
                            state = EndpointSessionState.Ready(1L),
                            generation = 1L,
                        )
                },
            )
        override val selectedEndpointId = MutableStateFlow(profiles.firstOrNull()?.id)

        override suspend fun start(legacyAddress: String?, legacyName: String?) = Unit

        override suspend fun stop() = Unit

        override suspend fun register(candidate: DiscoveredRadio, connect: Boolean): RadioEndpointProfile =
            error("Not used")

        override suspend fun select(endpointId: RadioEndpointId) {
            selectedEndpointId.value = endpointId
        }

        override suspend fun connect(endpointId: RadioEndpointId) = Unit

        override suspend fun disconnect(endpointId: RadioEndpointId) = Unit

        override suspend fun setLegacyPrimary(endpointId: RadioEndpointId) = Unit

        override suspend fun remove(endpointId: RadioEndpointId) = Unit

        override fun requireCurrentGeneration(endpointId: RadioEndpointId, expectedGeneration: Long) = Unit
    }
}
