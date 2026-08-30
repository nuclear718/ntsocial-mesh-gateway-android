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
package com.ntsocial.meshlink.ios.runtime

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.util.getChannel
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.conversation.ChannelSecurityKind
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearanceStore
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSnapshot
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSource
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelKey
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelRole
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelSummary
import com.ntsocial.meshlink.core.radiofleet.conversation.LocalChannelId
import com.ntsocial.meshlink.core.radiofleet.conversation.MutableEndpointConversationSourceRegistry
import com.ntsocial.meshlink.core.radiofleet.conversation.RegisteredEndpointConversationSource
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.scope.Scope
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings

internal class IosEndpointConversationSourceRegistry : MutableEndpointConversationSourceRegistry {
    private val mutableEntries =
        MutableStateFlow<Map<RadioEndpointId, RegisteredEndpointConversationSource>>(emptyMap())
    override val entries: StateFlow<Map<RadioEndpointId, RegisteredEndpointConversationSource>> =
        mutableEntries.asStateFlow()

    override fun register(runtimeToken: String, source: EndpointConversationSource) {
        require(runtimeToken.isNotBlank())
        mutableEntries.update { current ->
            current +
                (
                    source.endpointId to
                        RegisteredEndpointConversationSource(runtimeToken = runtimeToken, source = source)
                    )
        }
    }

    override fun unregister(endpointId: RadioEndpointId, runtimeToken: String) {
        mutableEntries.update { current ->
            if (current[endpointId]?.runtimeToken == runtimeToken) current - endpointId else current
        }
    }
}

internal class IosEndpointConversationSource(
    override val endpointId: RadioEndpointId,
    radioConfigRepository: RadioConfigRepository,
    packetRepository: PacketRepository,
    scope: CoroutineScope,
) : EndpointConversationSource {
    override val snapshot =
        radioConfigRepository.channelSetFlow
            .map { channelSet -> ChannelCatalog(channelSet, nowMillis) }
            .flatMapLatest { catalog -> catalog.observeSnapshot(packetRepository) }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = EndpointConversationSnapshot(endpointId),
            )

    private fun ChannelCatalog.observeSnapshot(packetRepository: PacketRepository) = channelSet.settings
        .mapIndexed { index, settings -> index to settings }
        .let { configuredChannels ->
            if (configuredChannels.isEmpty()) {
                flowOf(
                    EndpointConversationSnapshot(
                        endpointId = endpointId,
                        lastSuccessfulSyncAtMillis = loadedAtMillis,
                        hasCachedCatalog = false,
                    ),
                )
            } else {
                val unreadFlows =
                    configuredChannels.map { (index) -> packetRepository.getUnreadCountFlow(contactKey(index)) }
                combine(
                    packetRepository.getContacts(),
                    packetRepository.getContactSettings(),
                    combine(unreadFlows) { values -> values.toList() },
                ) { contacts, contactSettings, unreadCounts ->
                    EndpointConversationSnapshot(
                        endpointId = endpointId,
                        channels =
                        configuredChannels.mapIndexed { position, (index, settings) ->
                            val localContactKey = contactKey(index)
                            val lastPacket = contacts[localContactKey]
                            FleetChannelSummary(
                                key = FleetChannelKey(endpointId, LocalChannelId("meshtastic:$index")),
                                localContactKey = localContactKey,
                                channelIndex = index,
                                name = channelSet.getChannel(index)?.name ?: "Channel $index",
                                role = if (index == 0) FleetChannelRole.PRIMARY else FleetChannelRole.SECONDARY,
                                security = settings.securityKind(),
                                unreadCount = unreadCounts[position],
                                lastMessageText = lastPacket?.text,
                                lastMessageAtMillis = lastPacket?.time?.takeUnless { it == 0L },
                                isMuted = contactSettings[localContactKey]?.isMuted == true,
                            )
                        },
                        lastSuccessfulSyncAtMillis = loadedAtMillis,
                        hasCachedCatalog = true,
                    )
                }
            }
        }

    private fun ChannelSettings.securityKind(): ChannelSecurityKind = when {
        psk.size == 0 || (psk.size == 1 && psk[0].toInt() == 0) -> ChannelSecurityKind.CLEAR

        psk.size == 1 && (psk[0].toInt() and UNSIGNED_BYTE_MASK) in WELL_KNOWN_PSK_RANGE ->
            ChannelSecurityKind.WELL_KNOWN

        else -> ChannelSecurityKind.CUSTOM
    }

    private fun contactKey(index: Int): String = "$index${DataPacket.ID_BROADCAST}"

    private data class ChannelCatalog(val channelSet: ChannelSet, val loadedAtMillis: Long)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val UNSIGNED_BYTE_MASK = 0xff
        val WELL_KNOWN_PSK_RANGE = 1..10
    }
}

/** Publishes root and scoped endpoint channel histories using exact scope runtime tokens. */
internal class IosEndpointConversationSourceCoordinator(
    private val fleetManager: RadioFleetManager,
    private val scopeRegistry: IosRadioEndpointScopeRegistry,
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

    fun close() {
        sourceJob?.cancel()
        sourceJob = null
        managedSources.keys.toList().forEach(::removeSource)
        coordinatorScope.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reconcileSources(
        inputs: Pair<Map<RadioEndpointId, RadioEndpointSnapshot>, Map<RadioEndpointId, Scope>>,
    ) {
        val (snapshots, scopes) = inputs
        (managedSources.keys - snapshots.keys).forEach(::removeSource)
        snapshots.values.forEach { snapshot ->
            val endpointScope = scopes[snapshot.profile.id]
            val runtimeToken =
                if (snapshot.profile.legacyPrimary) {
                    "root:${snapshot.profile.id.value}"
                } else {
                    endpointScope?.id ?: return@forEach
                }
            if (managedSources[snapshot.profile.id]?.runtimeToken == runtimeToken) return@forEach

            val sourceScope = CoroutineScope(SupervisorJob() + sourceDispatcher)
            try {
                val source =
                    IosEndpointConversationSource(
                        endpointId = snapshot.profile.id,
                        radioConfigRepository = endpointScope?.get() ?: rootRadioConfigRepository,
                        packetRepository = endpointScope?.get() ?: rootPacketRepository,
                        scope = sourceScope,
                    )
                sourceRegistry.register(runtimeToken, source)
                managedSources.put(snapshot.profile.id, ManagedSource(runtimeToken, sourceScope))?.scope?.cancel()
            } catch (error: Exception) {
                sourceScope.cancel()
                Logger.w(error) { "Unable to prepare an iOS endpoint channel projection" }
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
