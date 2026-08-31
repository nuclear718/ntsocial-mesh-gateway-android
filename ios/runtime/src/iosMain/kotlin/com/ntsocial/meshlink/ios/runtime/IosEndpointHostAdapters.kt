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
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import org.meshtastic.proto.MeshPacket

/** Endpoint-local queue backed by the endpoint's own Room database. */
internal class IosEndpointMessageQueue(
    private val packetRepository: PacketRepository,
    private val radioController: Lazy<RadioController>,
    private val scope: CoroutineScope,
) : MessageQueue {
    private val mutex = Mutex()
    private val drainMutex = Mutex()
    private val pendingPacketIds = linkedSetOf<Int>()

    override suspend fun enqueue(packetId: Int) {
        mutex.withLock { pendingPacketIds += packetId }
        drain()
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun drain() {
        drainMutex.withLock {
            if (radioController.value.connectionState.value != ConnectionState.Connected) return@withLock
            val queuedIds = mutex.withLock { pendingPacketIds.toList() }
            queuedIds.forEach { packetId ->
                val packet = packetRepository.getPacketByPacketId(packetId)
                if (packet == null) {
                    mutex.withLock { pendingPacketIds -= packetId }
                    return@forEach
                }
                try {
                    radioController.value.sendMessage(packet)
                    packetRepository.updateMessageStatus(packet, MessageStatus.ENROUTE)
                    mutex.withLock { pendingPacketIds -= packetId }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Logger.w(error) { "iOS secondary endpoint send failed; queued row retained" }
                    packetRepository.updateMessageStatus(packet, MessageStatus.QUEUED)
                }
            }
        }
    }

    fun enqueueFromConnection(packetId: Int) {
        scope.launch { enqueue(packetId) }
    }
}

internal class IosEndpointMeshWorkerManager(private val messageQueue: IosEndpointMessageQueue) : MeshWorkerManager {
    override fun enqueueSendMessage(packetId: Int) = messageQueue.enqueueFromConnection(packetId)
}

/** Apple Gateway has no endpoint selector, so every non-primary iOS session fails closed at this boundary. */
internal class IosSecondaryGatewayRepository : NtsocialGatewayRepository {
    override val cachedEnvelopes = MutableStateFlow<List<NtsocialCachedEnvelope>>(emptyList())
    override val inboundSessionRevision = MutableStateFlow(0L)
    override val defaultChannelStatus = MutableStateFlow(NtsocialDefaultChannelStatus())

    override suspend fun activateInboundSession(expectedRadioSessionEpoch: Long): Boolean = false

    override fun invalidateInboundSession() {
        inboundSessionRevision.value += 1
    }

    override fun isInboundSessionActive(expectedRadioSessionEpoch: Long): Boolean = false

    override fun cacheInbound(packet: MeshPacket, dataPacket: DataPacket): Boolean = false

    override fun sendTestPayload(
        payload: ByteString,
        to: String?,
        channelIndex: Int,
        wantAck: Boolean,
        headerMsgId: ByteString?,
    ): NtsocialCachedEnvelope = secondaryGatewayUnavailable()

    override fun sendRawEnvelope(
        rawEnvelope: ByteString,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int?,
    ): NtsocialCachedEnvelope = secondaryGatewayUnavailable()

    override suspend fun persistAndQueueRawEnvelope(
        rawEnvelope: ByteString,
        sourceChannelId: String?,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): NtsocialCachedEnvelope = secondaryGatewayUnavailable()

    override suspend fun persistAndQueueNativeBroadcastText(
        text: String,
        sourceChannelId: String,
        channelIndex: Int,
        packetId: Int,
        originClientMessageId: String,
    ): DataPacket = secondaryGatewayUnavailable()

    override fun updateDefaultChannelStatus(status: NtsocialDefaultChannelStatus) {
        defaultChannelStatus.value = status
    }

    override fun clearCache() {
        cachedEnvelopes.value = emptyList()
    }

    private fun secondaryGatewayUnavailable(): Nothing =
        error("Apple Gateway is available only on the legacy-primary radio")
}
