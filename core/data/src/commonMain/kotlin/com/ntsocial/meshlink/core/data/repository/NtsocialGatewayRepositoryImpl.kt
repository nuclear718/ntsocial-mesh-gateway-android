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

import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.MeshPacket
import kotlin.random.Random

@Single(binds = [NtsocialGatewayRepository::class])
class NtsocialGatewayRepositoryImpl(
    private val commandSender: CommandSender,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : NtsocialGatewayRepository {
    private val _cachedEnvelopes = MutableStateFlow<List<NtsocialCachedEnvelope>>(emptyList())
    private val _defaultChannelStatus = MutableStateFlow(NtsocialDefaultChannelStatus())
    private val cacheMutex = Mutex()
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
                id = commandSender.generatePacketId(),
                channel = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
            )
        commandSender.sendData(dataPacket)

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
    }
}
