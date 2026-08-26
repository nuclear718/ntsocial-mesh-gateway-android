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

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayHistoryState
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearanceStore
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.service.NtsocialEndpointGatewaySource
import com.ntsocial.meshlink.core.service.NtsocialGatewayEndpointChannel
import com.ntsocial.meshlink.core.service.NtsocialGatewayEndpointMessageChange
import com.ntsocial.meshlink.core.service.NtsocialGatewayEndpointSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.ByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

internal class MeshtasticEndpointGatewaySource(
    private val profile: RadioEndpointProfile,
    private val endpointSnapshots: StateFlow<Map<RadioEndpointId, RadioEndpointSnapshot>>,
    private val appearanceStore: EndpointAppearanceStore,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
    private val gatewayRepository: NtsocialGatewayRepository,
    private val scope: CoroutineScope,
) : NtsocialEndpointGatewaySource {
    override val endpointId: String = profile.id.value
    private val channelSet =
        radioConfigRepository.channelSetFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ChannelSet(),
        )
    private val historyState =
        packetRepository
            .getGatewayHistoryState(emptyList())
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = NtsocialGatewayHistoryState(HISTORY_NOT_READY, 0L),
            )
    private val mutableRevision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = mutableRevision

    init {
        scope.launch { channelSet.collectLatest { mutableRevision.update { revision -> revision + 1L } } }
        scope.launch { historyState.collectLatest { mutableRevision.update { revision -> revision + 1L } } }
        scope.launch { endpointSnapshots.collectLatest { mutableRevision.update { revision -> revision + 1L } } }
        scope.launch {
            appearanceStore.appearances.collectLatest { mutableRevision.update { revision -> revision + 1L } }
        }
    }

    override suspend fun snapshot(): NtsocialGatewayEndpointSnapshot {
        val endpoint = endpointSnapshots.value[profile.id] ?: RadioEndpointSnapshot(profile)
        val set = channelSet.value
        val history = historyState.value
        val ready = endpoint.state is EndpointSessionState.Ready
        val localNodeId = gatewayLocalNodeId()
        val appearance = appearanceStore.appearances.value[profile.id]
        val endpointGeneration = endpointGeneration(endpoint)
        val catalogGeneration = "$endpointGeneration:${radioConfigRepository.channelSnapshotGeneration.value}"
        val channels = set.gatewayChannels()
        return NtsocialGatewayEndpointSnapshot(
            endpointId = endpointId,
            displayName = profile.displayName,
            addressSuffix = profile.addressSuffix,
            protocol = profile.protocol.name,
            sessionState = endpoint.state.gatewayName(),
            endpointGeneration = endpointGeneration,
            catalogGeneration = catalogGeneration,
            historyEpoch = history.historyEpoch.takeIf { it.isNotBlank() } ?: HISTORY_NOT_READY,
            messageChangeSeq = history.messageChangeSeq.coerceAtLeast(0L),
            nativeHistoryAvailable = history.historyEpoch != HISTORY_NOT_READY && channels.isNotEmpty(),
            nativeTextSendAvailable = ready && localNodeId != null && channels.isNotEmpty(),
            arbitraryRouteOverlayAvailable = ready && channels.isNotEmpty(),
            hasCachedCatalog = channels.isNotEmpty(),
            appearanceToken = appearance?.accentToken?.name,
            sortOrder = appearance?.sortOrder ?: Int.MAX_VALUE,
            channels = channels,
        )
    }

    override suspend fun messageChanges(after: Long, limit: Int): List<NtsocialGatewayEndpointMessageChange> {
        val localNodeId = gatewayLocalNodeId()
        return packetRepository.getGatewayStableMessageChanges(after = after, limit = limit).mapNotNull { change ->
            val identity = change.identity ?: return@mapNotNull null
            val packet = change.packet
            val fromNodeId =
                when (packet.from) {
                    DataPacket.ID_LOCAL -> localNodeId
                    else -> packet.from?.takeIf { it.isNotBlank() }
                } ?: return@mapNotNull null
            NtsocialGatewayEndpointMessageChange(
                sourceMessageId = identity.sourceMessageId,
                sourceChannelId = identity.sourceChannelId,
                originClientMessageId = change.originClientMessageId,
                changeSeq = change.changeSeq,
                packetId = packet.id.toLong() and UNSIGNED_INT_MASK,
                fromNodeId = fromNodeId,
                fromDisplayName = nodeRepository.getUser(fromNodeId).long_name,
                text = packet.text.orEmpty(),
                senderTimestampMillis = packet.time,
                receivedAtMillis = change.receivedAtMillis,
                direction = if (fromNodeId == localNodeId) "OUTBOUND" else "INBOUND",
                status = packet.status?.name,
                snr = packet.snr,
                rssi = packet.rssi,
                hopsAway = packet.hopsAway,
                viaMqtt = packet.viaMqtt,
            )
        }
    }

    override suspend fun sendOverlay(
        rawEnvelope: ByteString,
        sourceChannelId: String,
        channelIndex: Int,
        to: String?,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): Int = gatewayRepository
        .persistAndQueueRawEnvelope(
            rawEnvelope = rawEnvelope,
            sourceChannelId = sourceChannelId,
            to = to,
            channelIndex = channelIndex,
            hopLimit = hopLimit,
            wantAck = wantAck,
            packetId = packetId,
        )
        .packetId

    override suspend fun sendNativeText(
        text: String,
        sourceChannelId: String,
        channelIndex: Int,
        packetId: Int,
        originClientMessageId: String,
    ): Int = gatewayRepository
        .persistAndQueueNativeBroadcastText(
            text = text,
            sourceChannelId = sourceChannelId,
            channelIndex = channelIndex,
            packetId = packetId,
            originClientMessageId = originClientMessageId,
        )
        .id

    private fun ChannelSet.gatewayChannels(): List<NtsocialGatewayEndpointChannel> {
        val loraConfig = lora_config ?: Config.LoRaConfig()
        return settings.mapIndexed { index, settings ->
            val role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY
            val identity =
                NtsocialGatewayIdentity.channel(Channel(index = index, role = role, settings = settings), loraConfig)
            NtsocialGatewayEndpointChannel(
                sourceChannelId = identity.sourceChannelId,
                slotIndex = index,
                role = role.name,
                configuredName = identity.configuredName,
                displayName = identity.displayName,
                securityClass = identity.securityClass,
                uplinkEnabled = settings.uplink_enabled,
                downlinkEnabled = settings.downlink_enabled,
                canReadNativeText = true,
                canSendNativeText = true,
                canSendNtOverlay = true,
            )
        }
    }

    private fun gatewayLocalNodeId(): String? {
        val ourNode = nodeRepository.ourNodeInfo.value
        val nodeNum = ourNode?.num?.takeIf { it != 0 } ?: nodeRepository.myNodeInfo.value?.myNodeNum?.takeIf { it != 0 }
        return NtsocialGatewayIdentity.stableLocalNodeId(
            userId = ourNode?.user?.id,
            myId = nodeRepository.myId.value,
            nodeNum = nodeNum,
        )
    }

    private fun endpointGeneration(snapshot: RadioEndpointSnapshot): String =
        "${profile.id.value}:${snapshot.generation}"

    private fun EndpointSessionState.gatewayName(): String = when (this) {
        is EndpointSessionState.Ready -> "READY"
        EndpointSessionState.Connecting -> "CONNECTING"
        EndpointSessionState.Synchronizing -> "SYNCHRONIZING"
        EndpointSessionState.WaitingResource -> "WAITING_RESOURCE"
        EndpointSessionState.Registered -> "REGISTERED"
        is EndpointSessionState.Degraded -> "DEGRADED"
        is EndpointSessionState.Failed -> "FAILED"
    }

    private companion object {
        const val HISTORY_NOT_READY = "not-ready"
        const val UNSIGNED_INT_MASK = 0xFFFF_FFFFL
    }
}
