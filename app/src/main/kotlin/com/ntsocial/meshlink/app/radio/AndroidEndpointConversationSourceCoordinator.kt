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
package com.ntsocial.meshlink.app.radio

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearanceStore
import com.ntsocial.meshlink.core.radiofleet.conversation.MutableEndpointConversationSourceRegistry
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

@Single
class AndroidEndpointConversationSourceCoordinator(
    private val fleetManager: RadioFleetManager,
    private val scopeRegistry: RadioEndpointScopeRegistry,
    private val sourceRegistry: MutableEndpointConversationSourceRegistry,
    private val appearanceStore: EndpointAppearanceStore,
    private val rootRadioConfigRepository: RadioConfigRepository,
    private val rootPacketRepository: PacketRepository,
    dispatchers: CoroutineDispatchers,
) {
    private val sourceDispatcher = dispatchers.default
    private val coordinatorScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val managedSources = mutableMapOf<RadioEndpointId, ManagedSource>()
    private var sourceJob: Job? = null

    fun start() {
        if (sourceJob != null) return
        sourceJob =
            coordinatorScope.launch {
                combine(fleetManager.snapshots, scopeRegistry.scopes, ::Pair).collect(::reconcileSources)
            }
        coordinatorScope.launch {
            fleetManager.snapshots
                .map { snapshots ->
                    snapshots.values
                        .sortedWith(
                            compareByDescending<RadioEndpointSnapshot> { it.profile.legacyPrimary }
                                .thenByDescending { it.profile.priority }
                                .thenBy { it.profile.displayName.lowercase() },
                        )
                        .map { it.profile }
                }
                .distinctUntilChanged()
                .collect(appearanceStore::reconcile)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reconcileSources(
        inputs: Pair<Map<RadioEndpointId, RadioEndpointSnapshot>, Map<RadioEndpointId, Scope>>,
    ) {
        val (snapshots, scopes) = inputs
        val expectedIds = snapshots.keys
        (managedSources.keys - expectedIds).forEach(::removeSource)

        snapshots.values.forEach { snapshot ->
            val endpointId = snapshot.profile.id
            val endpointScope = scopes[endpointId]
            val runtimeToken =
                if (snapshot.profile.legacyPrimary) {
                    "root:${endpointId.value}"
                } else {
                    endpointScope?.id ?: return@forEach
                }
            if (managedSources[endpointId]?.runtimeToken == runtimeToken) return@forEach

            val sourceScope = CoroutineScope(SupervisorJob() + sourceDispatcher)
            try {
                val source =
                    if (snapshot.profile.legacyPrimary) {
                        MeshtasticEndpointConversationSource(
                            endpointId = endpointId,
                            radioConfigRepository = rootRadioConfigRepository,
                            packetRepository = rootPacketRepository,
                            scope = sourceScope,
                        )
                    } else {
                        checkNotNull(endpointScope)
                        MeshtasticEndpointConversationSource(
                            endpointId = endpointId,
                            radioConfigRepository = endpointScope.get(),
                            packetRepository = endpointScope.get(),
                            scope = sourceScope,
                        )
                    }
                val replacement = ManagedSource(runtimeToken, sourceScope)
                sourceRegistry.register(runtimeToken, source)
                managedSources.put(endpointId, replacement)?.scope?.cancel()
            } catch (error: Exception) {
                sourceScope.cancel()
                Logger.w { "Unable to prepare an endpoint channel projection: ${error::class.simpleName}" }
            }
        }
    }

    private fun removeSource(endpointId: RadioEndpointId) {
        val removed = managedSources.remove(endpointId) ?: return
        sourceRegistry.unregister(endpointId, removed.runtimeToken)
        removed.scope.cancel()
    }

    private data class ManagedSource(val runtimeToken: String, val scope: CoroutineScope)
}
