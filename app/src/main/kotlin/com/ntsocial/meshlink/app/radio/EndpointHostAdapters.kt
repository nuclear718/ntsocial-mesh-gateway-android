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

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry

/** Secondary radios must not emit the endpoint-less legacy Android broadcasts. */
internal object EndpointServiceBroadcasts : ServiceBroadcasts {
    override fun subscribeReceiver(receiverName: String, packageName: String) = Unit

    override fun broadcastReceivedData(dataPacket: DataPacket) = Unit

    override fun broadcastConnection() = Unit

    override fun broadcastNodeChange(node: Node) = Unit

    override fun broadcastMessageStatus(packetId: Int, status: MessageStatus) = Unit
}

/** The foreground service notification remains an aggregate owned by the legacy-primary service. */
@Suppress("TooManyFunctions") // The host interface currently requires thirteen notification callbacks.
internal object EndpointServiceNotifications : MeshServiceNotifications {
    override fun clearNotifications() = Unit

    override fun initChannels() = Unit

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) = Unit

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) = Unit

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) = Unit

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) = Unit

    override fun showAlertNotification(contactKey: String, name: String, alert: String) = Unit

    override fun showNewNodeSeenNotification(node: Node) = Unit

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) = Unit

    override fun showClientNotification(clientNotification: ClientNotification) = Unit

    override fun cancelMessageNotification(contactKey: String) = Unit

    override fun cancelLowBatteryNotification(node: Node) = Unit

    override fun clearClientNotification(notification: ClientNotification) = Unit
}

/** Phone-location sharing remains legacy-primary-only in the first multi-radio generation. */
internal object EndpointMeshLocationManager : MeshLocationManager {
    override fun start(scope: CoroutineScope, sendPositionFn: (Position) -> Unit) = Unit

    override fun restart() = Unit

    override fun setLocationAccessAllowed(allowed: Boolean) = Unit

    override fun stop() = Unit
}

internal object EndpointAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() = Unit
}

/**
 * A process-local worker facade whose durable source of truth is the endpoint's own Room database. On every connected
 * transition, [MeshConnectionManager] asks this facade to re-enqueue all rows still marked QUEUED.
 */
internal class EndpointMessageQueue(
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
                    co.touchlab.kermit.Logger.w(error) { "Secondary endpoint send failed; queued row retained" }
                    packetRepository.updateMessageStatus(packet, MessageStatus.QUEUED)
                }
            }
        }
    }

    fun enqueueFromConnection(packetId: Int) {
        scope.launch { enqueue(packetId) }
    }
}

internal class EndpointMeshWorkerManager(private val messageQueue: EndpointMessageQueue) : MeshWorkerManager {
    override fun enqueueSendMessage(packetId: Int) = messageQueue.enqueueFromConnection(packetId)
}

/** Gateway v1/v2 has no endpoint selector, so every non-primary session fails closed at this boundary. */
internal class SecondaryGatewayRepository : NtsocialGatewayRepository {
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
        error("Android Gateway v1/v2 is available only on the legacy-primary radio")
}
