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
@file:Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")

package com.ntsocial.meshlink.ios.runtime

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.GatewayPacketDispatchResult
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

/**
 * iOS durable outbox drainer.
 *
 * The Room packet row is inserted with [MessageStatus.QUEUED] before [enqueue] is called, so that row is the durable
 * work record. This owner verifies that admission, then replays all queued rows on process start and every reconnect.
 * iOS suspension can delay the drain, but it cannot turn a pending row into a false delivery success.
 */
internal class IosDurableMessageQueue(
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
    private val commandSender: CommandSender,
    private val radioConfigRepository: RadioConfigRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val gatewayIngressSessionGate: GatewayIngressSessionGate,
    dispatchers: CoroutineDispatchers,
) : MessageQueue {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val drainSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val drainMutex = Mutex()
    private var connectionJob: Job? = null
    private var gatewaySessionJob: Job? = null
    private var signalJob: Job? = null

    fun start() {
        if (connectionJob?.isActive == true) return
        connectionJob =
            radioController.connectionState
                .filter { it == ConnectionState.Connected }
                .onEach { drain() }
                .launchIn(scope)
        gatewaySessionJob =
            gatewayIngressSessionGate.activeSessionEpoch.onEach { drainSignals.tryEmit(Unit) }.launchIn(scope)
        signalJob = drainSignals.onEach { drain() }.launchIn(scope)
        scope.launch { drain() }
    }

    override suspend fun enqueue(packetId: Int) {
        require(packetId > 0) { "packetId must be positive" }
        val packet =
            requireNotNull(packetRepository.getPacketByPacketId(packetId)) {
                "Durable iOS queue admission requires an existing Room packet"
            }
        require(packet.status == MessageStatus.QUEUED || packet.status == MessageStatus.ENROUTE) {
            "Durable iOS queue admission requires QUEUED or already-admitted ENROUTE state"
        }
        drainSignals.tryEmit(Unit)
    }

    @Suppress("TooGenericExceptionCaught")
    internal suspend fun drain() = drainMutex.withLock {
        if (radioController.connectionState.value != ConnectionState.Connected) return@withLock
        for (queued in packetRepository.getDurableQueuedPackets().sortedBy { it.packet.time }) {
            val packet = queued.packet
            if (radioController.connectionState.value != ConnectionState.Connected) break
            try {
                val expectedSource = queued.expectedSourceChannelId
                if (expectedSource == null) {
                    // Non-Gateway outbox rows retain the existing iOS replay behavior.
                    radioController.sendMessage(packet)
                    packetRepository.updateMessageStatus(packet, MessageStatus.ENROUTE)
                    continue
                }

                val session = radioInterfaceService.radioSessionState.value
                if (!session.isConfiguredReady || !gatewayIngressSessionGate.isActive(session.epoch)) {
                    // Configuration or selection is transiently unavailable. Keep the durable row queued.
                    break
                }
                val channelSet = radioConfigRepository.channelSetFlow.first()
                if (channelSet.sourceChannelId(packet.channel) != expectedSource) {
                    Logger.w {
                        "iOS Gateway packet ${packet.id} failed closed because its durable channel identity changed"
                    }
                    packetRepository.updateMessageStatus(packet, MessageStatus.ERROR)
                    continue
                }

                // PacketHandler owns ChannelOperationLock from its actual dequeue/source revalidation through the
                // exact transport send and matching QueueStatus. This call therefore cannot return while an orphan
                // queued item could later cross a same-session channel mutation.
                when (
                    commandSender.sendDataAwaitForGatewaySession(
                        p = packet,
                        expectedRadioSessionEpoch = session.epoch,
                        expectedSourceChannelId = expectedSource,
                    )
                ) {
                    GatewayPacketDispatchResult.ACCEPTED ->
                        packetRepository.updateMessageStatus(packet, MessageStatus.ENROUTE)

                    GatewayPacketDispatchResult.SOURCE_IDENTITY_MISMATCH ->
                        packetRepository.updateMessageStatus(packet, MessageStatus.ERROR)

                    GatewayPacketDispatchResult.TRANSIENT_FAILURE -> {
                        packetRepository.updateMessageStatus(packet, MessageStatus.QUEUED)
                        break
                    }
                }
            } catch (error: Exception) {
                Logger.w(error) { "iOS queued packet ${packet.id} remains pending" }
                packetRepository.updateMessageStatus(packet, MessageStatus.QUEUED)
                break
            }
        }
    }

    fun close() {
        connectionJob?.cancel()
        gatewaySessionJob?.cancel()
        signalJob?.cancel()
        scope.cancel()
    }
}

private fun ChannelSet.sourceChannelId(slotIndex: Int): String? {
    val settings = settings.getOrNull(slotIndex) ?: return null
    val role = if (slotIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY
    return NtsocialGatewayIdentity.channel(
        Channel(index = slotIndex, role = role, settings = settings),
        lora_config ?: Config.LoRaConfig(),
    )
        .sourceChannelId
}
