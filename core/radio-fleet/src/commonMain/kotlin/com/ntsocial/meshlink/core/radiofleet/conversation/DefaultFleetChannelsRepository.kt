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

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class DefaultFleetChannelsRepository(
    fleetManager: RadioFleetManager,
    sourceRegistry: EndpointConversationSourceRegistry,
    appearanceStore: EndpointAppearanceStore,
    scope: CoroutineScope,
) : FleetChannelsRepository {
    override val groups =
        combine(fleetManager.snapshots, sourceRegistry.entries, appearanceStore.appearances, ::FleetInputs)
            .flatMapLatest(::observeGroups)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private fun observeGroups(inputs: FleetInputs): Flow<List<FleetChannelGroup>> {
        val baseOrder =
            inputs.snapshots.values.sortedWith(
                compareByDescending<RadioEndpointSnapshot> { it.profile.legacyPrimary }
                    .thenByDescending { it.profile.priority }
                    .thenBy { it.profile.displayName.lowercase() },
            )
        val fallbackAppearances =
            baseOrder.mapIndexed { index, snapshot -> snapshot.profile.id to defaultEndpointAppearance(index) }.toMap()
        val ordered =
            baseOrder.sortedWith(
                compareBy<RadioEndpointSnapshot> {
                    inputs.appearances[it.profile.id]?.sortOrder
                        ?: fallbackAppearances.getValue(it.profile.id).sortOrder
                }
                    .thenByDescending { it.profile.legacyPrimary }
                    .thenByDescending { it.profile.priority }
                    .thenBy { it.profile.displayName.lowercase() },
            )
        if (ordered.isEmpty()) return flowOf(emptyList())

        val sourceFlows =
            ordered.map { endpoint ->
                inputs.sources[endpoint.profile.id]?.source?.snapshot
                    ?: flowOf(EndpointConversationSnapshot(endpoint.profile.id))
            }
        return combine(sourceFlows) { sourceSnapshots ->
            ordered.mapIndexed { index, endpoint ->
                val sourceSnapshot = sourceSnapshots[index]
                FleetChannelGroup(
                    profile = endpoint.profile,
                    sessionState = endpoint.state,
                    generation = endpoint.generation,
                    appearance =
                    inputs.appearances[endpoint.profile.id] ?: fallbackAppearances.getValue(endpoint.profile.id),
                    channels = sourceSnapshot.channels,
                    lastSuccessfulSyncAtMillis = sourceSnapshot.lastSuccessfulSyncAtMillis,
                    hasCachedCatalog = sourceSnapshot.hasCachedCatalog,
                    dataAvailable = inputs.sources.containsKey(endpoint.profile.id),
                )
            }
        }
    }

    private data class FleetInputs(
        val snapshots: Map<com.ntsocial.meshlink.core.radiofleet.RadioEndpointId, RadioEndpointSnapshot>,
        val sources: Map<com.ntsocial.meshlink.core.radiofleet.RadioEndpointId, RegisteredEndpointConversationSource>,
        val appearances: Map<com.ntsocial.meshlink.core.radiofleet.RadioEndpointId, EndpointAppearance>,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
