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
@file:Suppress("MagicNumber")

package com.ntsocial.meshlink.core.data.repository

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageChange
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayNativeText
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import kotlin.random.Random

@Single(binds = [NtsocialGatewayRepository::class])
class NtsocialGatewayRepositoryImpl(
    private val commandSender: CommandSender,
    private val packetRepository: PacketRepository,
    private val messageQueue: MessageQueue,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : NtsocialGatewayRepository {
    private val _cachedEnvelopes = MutableStateFlow<List<NtsocialCachedEnvelope>>(emptyList())
    private val _defaultChannelStatus = MutableStateFlow(NtsocialDefaultChannelStatus())
    private val cacheMutex = Mutex()
    private val nativeTextMutex = Mutex()
    private val seenCacheKeys = mutableSetOf<String>()

    override val cachedEnvelopes: StateFlow<List<NtsocialCachedEnvelope>> = _cachedEnvelopes.asStateFlow()

    override val defaultChannelStatus: StateFlow<NtsocialDefaultChannelStatus> = _defaultChannelStatus.asStateFlow()

    override fun cacheInbound(packet: MeshPacket, dataPacket: DataPacket): Boolean {
        val record =
            toCacheRecord(packet = packet, dataPacket = dataPacket, direction = NtsocialEnvelopeDirection.INBOUND)
                ?: return false

        cache(record)
        return true
    }

    override fun sendTestPayload(
        payload: ByteString,
        to: String?,
        channelIndex: Int,
        wantAck: Boolean,
        headerMsgId: ByteString?,
    ): NtsocialCachedEnvelope {
        val msgId = headerMsgId ?: randomHeaderMsgId()
        val rawEnvelope = NtsocialEnvelopeCodec.encode(headerMsgId = msgId, payload = payload)
        val dataPacket =
            DataPacket(
                to = to,
                bytes = rawEnvelope,
                dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                id = commandSender.generatePacketId(),
                channel = channelIndex,
                wantAck = wantAck,
            )

        commandSender.sendData(dataPacket)

        val record =
            NtsocialCachedEnvelope(
                direction = NtsocialEnvelopeDirection.OUTBOUND,
                envelope = requireNotNull(NtsocialEnvelopeCodec.decode(rawEnvelope)),
                rawBytes = rawEnvelope,
                packetId = dataPacket.id,
                from = dataPacket.from,
                to = dataPacket.to,
                channelIndex = dataPacket.channel,
                portNum = dataPacket.dataType,
                cachedAtMillis = nowMillis,
            )
        cache(record)
        return record
    }

    override fun sendRawEnvelope(
        rawEnvelope: ByteString,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int?,
    ): NtsocialCachedEnvelope {
        require(rawEnvelope.size <= NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES) {
            "NTsocial command envelope exceeds the external gateway limit"
        }
        require(channelIndex >= 0) { "channelIndex must not be negative" }
        require(hopLimit >= 0) { "hopLimit must not be negative" }

        val envelope = requireNotNull(NtsocialEnvelopeCodec.decode(rawEnvelope)) { "Invalid NTsocial command envelope" }
        val dataPacket =
            DataPacket(
                to = to,
                bytes = rawEnvelope,
                dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                id = packetId ?: commandSender.generatePacketId(),
                channel = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
            )
        Logger.i {
            "ntsocial_gateway_tx stage=data_packet packetId=${dataPacket.id} channelIndex=${dataPacket.channel} " +
                "port=${dataPacket.dataType} bytes=${rawEnvelope.size} wantAck=$wantAck"
        }
        commandSender.sendData(dataPacket)
        Logger.i {
            "ntsocial_gateway_tx stage=command_sender_return packetId=${dataPacket.id} status=${dataPacket.status}"
        }

        return NtsocialCachedEnvelope(
            direction = NtsocialEnvelopeDirection.OUTBOUND,
            envelope = envelope,
            rawBytes = rawEnvelope,
            packetId = dataPacket.id,
            from = dataPacket.from,
            to = dataPacket.to,
            channelIndex = dataPacket.channel,
            portNum = dataPacket.dataType,
            cachedAtMillis = nowMillis,
        )
            .also(::cache)
    }

    override suspend fun persistAndQueueRawEnvelope(
        rawEnvelope: ByteString,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): NtsocialCachedEnvelope {
        val (packet, record) =
            prepareRawEnvelope(
                rawEnvelope = rawEnvelope,
                to = to,
                channelIndex = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
                packetId = packetId,
            )
        val existing = packetRepository.getPacketByPacketId(packetId)
        when {
            existing == null ->
                packetRepository.savePacket(
                    myNodeNum = 0,
                    contactKey = "$channelIndex${to ?: DataPacket.ID_BROADCAST}",
                    packet = packet,
                    receivedTime = nowMillis,
                )

            !existing.matchesDurableGatewayPacket(packet) ->
                throw IllegalArgumentException("Gateway packet ID already belongs to different content")
        }

        if (existing == null || existing.status == MessageStatus.QUEUED) {
            messageQueue.enqueue(packetId)
        }
        cache(record)
        return record
    }

    override suspend fun persistAndQueueNativeBroadcastText(
        text: String,
        sourceChannelId: String,
        channelIndex: Int,
        packetId: Int,
        originClientMessageId: String,
    ): DataPacket {
        require(NtsocialGatewayNativeText.isValid(text)) { "Native channel text is empty or exceeds the UTF-8 limit" }
        require(channelIndex >= 0) { "channelIndex must not be negative" }
        require(packetId > 0) { "packetId must be positive" }
        require(CLIENT_MESSAGE_ID_REGEX.matches(originClientMessageId)) { "originClientMessageId must be canonical" }

        return nativeTextMutex.withLock {
            val channelSet = radioConfigRepository.channelSetFlow.first()
            val settings =
                requireNotNull(channelSet.settings.getOrNull(channelIndex)) { "Gateway route no longer exists" }
            val channelIdentity =
                NtsocialGatewayIdentity.channel(
                    Channel(
                        index = channelIndex,
                        role = if (channelIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
                        settings = settings,
                    ),
                    channelSet.lora_config ?: Config.LoRaConfig(),
                )
            require(channelIdentity.sourceChannelId == sourceChannelId) {
                "Gateway route no longer matches its channel"
            }

            val ourNode = nodeRepository.ourNodeInfo.value
            val localNodeNum =
                ourNode?.num?.takeIf { it != 0 } ?: nodeRepository.myNodeInfo.value?.myNodeNum?.takeIf { it != 0 }
            val localNodeId =
                requireNotNull(
                    NtsocialGatewayIdentity.stableLocalNodeId(
                        userId = ourNode?.user?.id,
                        myId = nodeRepository.myId.value,
                        nodeNum = localNodeNum,
                    ),
                ) {
                    "Stable local node identity is not ready"
                }
            val packet =
                DataPacket(to = DataPacket.ID_BROADCAST, channel = channelIndex, text = text).apply {
                    from = localNodeId
                    id = packetId
                    status = MessageStatus.QUEUED
                    time = nowMillis
                }
            val gatewayIdentity =
                requireNotNull(NtsocialGatewayIdentity.nativeBroadcastText(channelIdentity, packet)) {
                    "Native channel text did not produce a stable Gateway identity"
                }

            val existing = packetRepository.getGatewayMessageChangeByPacketId(packetId)
            when {
                existing == null ->
                    packetRepository.savePacket(
                        myNodeNum = localNodeNum ?: 0,
                        contactKey = "$channelIndex${DataPacket.ID_BROADCAST}",
                        packet = packet,
                        receivedTime = packet.time,
                        gatewayIdentity = gatewayIdentity,
                        originClientMessageId = originClientMessageId,
                    )

                !existing.matchesDurableNativeText(packet, gatewayIdentity, originClientMessageId) ->
                    throw IllegalArgumentException("Gateway packet ID already belongs to different content")
            }

            if (existing == null || existing.packet.status == MessageStatus.QUEUED) {
                messageQueue.enqueue(packetId)
            }
            existing?.packet ?: packet
        }
    }

    override fun updateDefaultChannelStatus(status: NtsocialDefaultChannelStatus) {
        _defaultChannelStatus.value = status
    }

    override fun clearCache() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cacheMutex.withLock {
                seenCacheKeys.clear()
                _cachedEnvelopes.value = emptyList()
            }
        }
    }

    private fun toCacheRecord(
        packet: MeshPacket,
        dataPacket: DataPacket,
        direction: NtsocialEnvelopeDirection,
    ): NtsocialCachedEnvelope? {
        val rawBytes = dataPacket.bytes
        val envelope =
            rawBytes
                ?.takeIf { NtsocialTransport.isInboundPort(dataPacket.dataType) }
                ?.let(NtsocialEnvelopeCodec::decode)

        return envelope?.let {
            NtsocialCachedEnvelope(
                direction = direction,
                envelope = it,
                rawBytes = rawBytes,
                packetId = packet.id,
                from = dataPacket.from,
                to = dataPacket.to,
                channelIndex = dataPacket.channel,
                portNum = dataPacket.dataType,
                cachedAtMillis = nowMillis,
            )
        }
    }

    private fun prepareRawEnvelope(
        rawEnvelope: ByteString,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): Pair<DataPacket, NtsocialCachedEnvelope> {
        require(rawEnvelope.size <= NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES) {
            "NTsocial command envelope exceeds the external gateway limit"
        }
        require(channelIndex >= 0) { "channelIndex must not be negative" }
        require(hopLimit >= 0) { "hopLimit must not be negative" }
        require(packetId > 0) { "packetId must be positive" }

        val envelope = requireNotNull(NtsocialEnvelopeCodec.decode(rawEnvelope)) { "Invalid NTsocial command envelope" }
        val packet =
            DataPacket(
                to = to,
                bytes = rawEnvelope,
                dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                id = packetId,
                channel = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
            )
                .apply {
                    from = DataPacket.ID_LOCAL
                    status = MessageStatus.QUEUED
                    time = nowMillis
                }
        return packet to
            NtsocialCachedEnvelope(
                direction = NtsocialEnvelopeDirection.OUTBOUND,
                envelope = envelope,
                rawBytes = rawEnvelope,
                packetId = packet.id,
                from = packet.from,
                to = packet.to,
                channelIndex = packet.channel,
                portNum = packet.dataType,
                cachedAtMillis = nowMillis,
            )
    }

    private fun DataPacket.matchesDurableGatewayPacket(expected: DataPacket): Boolean = id == expected.id &&
        bytes == expected.bytes &&
        dataType == expected.dataType &&
        to == expected.to &&
        channel == expected.channel &&
        hopLimit == expected.hopLimit &&
        wantAck == expected.wantAck

    private fun NtsocialGatewayMessageChange.matchesDurableNativeText(
        expectedPacket: DataPacket,
        expectedIdentity: NtsocialGatewayMessageIdentity,
        expectedOriginClientMessageId: String,
    ): Boolean = packet.id == expectedPacket.id &&
        packet.bytes == expectedPacket.bytes &&
        packet.dataType == expectedPacket.dataType &&
        packet.from == expectedPacket.from &&
        packet.to == DataPacket.ID_BROADCAST &&
        packet.channel == expectedPacket.channel &&
        packet.hopLimit == expectedPacket.hopLimit &&
        packet.wantAck == expectedPacket.wantAck &&
        identity == expectedIdentity &&
        originClientMessageId == expectedOriginClientMessageId

    private fun cache(record: NtsocialCachedEnvelope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cacheMutex.withLock {
                if (!seenCacheKeys.add(record.cacheKey)) return@withLock

                val next = (_cachedEnvelopes.value + record).takeLast(NtsocialTransport.MAX_CACHED_ENVELOPES)
                if (next.size == NtsocialTransport.MAX_CACHED_ENVELOPES) {
                    seenCacheKeys.clear()
                    seenCacheKeys.addAll(next.map { it.cacheKey })
                }
                _cachedEnvelopes.value = next
            }
        }
    }

    private fun randomHeaderMsgId(): ByteString = ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) {
        Random.nextInt(from = 0, until = RANDOM_BYTE_EXCLUSIVE).toByte()
    }
        .toByteString()

    private companion object {
        const val RANDOM_BYTE_EXCLUSIVE = 256
        val CLIENT_MESSAGE_ID_REGEX = Regex("^[0-9A-F]{32}$")
    }
}
