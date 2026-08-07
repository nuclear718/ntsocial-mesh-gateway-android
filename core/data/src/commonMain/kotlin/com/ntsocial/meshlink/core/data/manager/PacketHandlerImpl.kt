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
@file:Suppress("LoopWithTooManyJumpStatements", "ReturnCount")

package com.ntsocial.meshlink.core.data.manager

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.handledLaunch
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MeshLog
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioNotConnectedException
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.model.util.toOneLineString
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.GatewayPacketDispatchResult
import com.ntsocial.meshlink.core.repository.MeshLogRepository
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import org.meshtastic.proto.Channel as ProtoChannel

@Suppress("TooManyFunctions")
@Single
class PacketHandlerImpl(
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val serviceRepository: ServiceRepository,
    private val ingressWorkTracker: RadioIngressWorkTracker,
    private val channelOperationLock: ChannelOperationLock,
    private val radioConfigRepository: RadioConfigRepository,
    private val gatewayIngressSessionGate: GatewayIngressSessionGate,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : PacketHandler {

    companion object {
        private val TIMEOUT = 5.seconds
    }

    private var queueJob: Job? = null

    private val queueMutex = Mutex()
    private val admissionMutex = Mutex()
    private val queuedPackets = mutableListOf<QueuedPacket>()
    private val outboundGeneration = atomic(1L)
    private val outboundAccepting = atomic(true)
    private val outboundWorkTracker = RadioIngressWorkTracker()

    // Unbounded channel preserves FIFO ordering of fire-and-forget sendToRadio(MeshPacket)
    // calls. The non-suspend entry point does trySend (always succeeds for UNLIMITED) and
    // a single consumer coroutine enqueues packets under queueMutex in arrival order.
    private val outboundChannel = Channel<OutboundQueueItem>(Channel.UNLIMITED)

    // Set to true by stopPacketQueue() under queueMutex. Checked by startPacketQueueLocked()
    // and the queue processor's finally block to prevent restarting a stopped queue.
    private var queueStopped = false

    private val responseMutex = Mutex()
    private val queueResponse = mutableMapOf<Int, CompletableDeferred<Boolean>>()

    init {
        // Single consumer serializes enqueues from the non-suspend sendToRadio(MeshPacket)
        // entry point, preserving FIFO across rapid concurrent callers.
        scope.launch { outboundChannel.consumeAsFlow().collect(::admitOutboundItem) }
    }

    override fun sendToRadio(p: ToRadio) {
        val b = p.encode()
        Logger.d { "Sending ${b.size} bytes to radio" }

        radioInterfaceService.sendToRadio(b)
        recordOutboundSideEffects(p)
    }

    private fun recordOutboundSideEffects(p: ToRadio) {
        p.packet?.id?.let { changeStatus(it, MessageStatus.ENROUTE) }

        val packet = p.packet
        if (packet?.decoded != null) {
            val packetToSave =
                MeshLog(
                    uuid = Uuid.random().toString(),
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = packet.toString(),
                    fromNum = MeshLog.NODE_NUM_LOCAL,
                    portNum = packet.decoded?.portnum?.value ?: 0,
                    fromRadio = FromRadio(packet = packet),
                )
            insertMeshLog(packetToSave)
        }
    }

    override fun sendToRadioForSession(p: ToRadio, expectedRadioSessionEpoch: Long): Boolean {
        val bytes = p.encode()
        Logger.d { "Sending ${bytes.size} bytes to exact radio session $expectedRadioSessionEpoch" }
        val admitted = radioInterfaceService.sendToRadioForSession(bytes, expectedRadioSessionEpoch)
        if (admitted) recordOutboundSideEffects(p)
        return admitted
    }

    override fun sendToRadio(packet: MeshPacket) {
        // Non-suspend entry point — order-preserving via unbounded channel drained by
        // a single consumer coroutine. trySend on UNLIMITED never fails for capacity.
        val item = tagOutboundIfAccepting(packet)
        val enqueueResult = item?.let(outboundChannel::trySend)
        if (packet.decoded?.portnum?.value == NtsocialTransport.PRIVATE_APP_PORT_NUM) {
            Logger.i {
                "ntsocial_gateway_tx stage=packet_handler_enqueue packetId=${packet.id} " +
                    "channelIndex=${packet.channel} connectionState=${serviceRepository.connectionState.value} " +
                    "accepted=${enqueueResult?.isSuccess == true}"
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean =
        sendToRadioAndAwait(packet = packet, expectedRadioSessionEpoch = null)

    override suspend fun sendToRadioAndAwaitForSession(packet: MeshPacket, expectedRadioSessionEpoch: Long): Boolean =
        sendToRadioAndAwait(packet = packet, expectedRadioSessionEpoch = expectedRadioSessionEpoch)

    override suspend fun sendToRadioAndAwaitForGatewaySession(
        packet: MeshPacket,
        expectedRadioSessionEpoch: Long,
        expectedSourceChannelId: String,
    ): Boolean = dispatchGatewayPacketAndAwait(packet, expectedRadioSessionEpoch, expectedSourceChannelId) ==
        GatewayPacketDispatchResult.ACCEPTED

    override suspend fun dispatchGatewayPacketAndAwait(
        packet: MeshPacket,
        expectedRadioSessionEpoch: Long,
        expectedSourceChannelId: String,
    ): GatewayPacketDispatchResult = channelOperationLock.withLock {
        dispatchGatewayPacketAndAwaitLocked(packet, expectedRadioSessionEpoch, expectedSourceChannelId)
    }

    private suspend fun dispatchGatewayPacketAndAwaitLocked(
        packet: MeshPacket,
        expectedRadioSessionEpoch: Long,
        expectedSourceChannelId: String,
    ): GatewayPacketDispatchResult {
        val completion = CompletableDeferred<GatewayPacketDispatchResult>()
        val queuedPacket =
            QueuedPacket(
                packet = packet,
                expectedRadioSessionEpoch = expectedRadioSessionEpoch,
                expectedSourceChannelId = expectedSourceChannelId,
                gatewayCompletion = completion,
            )
        val admitted =
            admissionMutex.withLock {
                val capturedGeneration = outboundGeneration.value
                if (!outboundAccepting.value || queueStopped || !isExpectedSessionReady(expectedRadioSessionEpoch)) {
                    return@withLock false
                }
                queueMutex.withLock {
                    if (capturedGeneration != outboundGeneration.value || queueStopped) return@withLock false
                    queuedPackets.add(queuedPacket)
                    startPacketQueueLocked()
                    true
                }
            }
        if (!admitted) return GatewayPacketDispatchResult.TRANSIENT_FAILURE
        return try {
            completion.await()
        } catch (cancelled: CancellationException) {
            val removed = queueMutex.withLock { queuedPackets.remove(queuedPacket) }
            if (removed) {
                completion.complete(GatewayPacketDispatchResult.TRANSIENT_FAILURE)
            } else {
                // Once dequeued, the worker owns the operation-lock/QueueStatus linearization. Do not let caller
                // cancellation release its logical ownership while a radio admission can still occur.
                withContext(NonCancellable) { completion.await() }
            }
            throw cancelled
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun sendToRadioAndAwait(
        packet: MeshPacket,
        expectedRadioSessionEpoch: Long?,
        expectedSourceChannelId: String? = null,
    ): Boolean {
        // Pre-register the deferred so the queue processor and QueueStatus handler
        // can find it immediately — no polling required.
        val deferred = CompletableDeferred<Boolean>()
        val dispatchCompletion = CompletableDeferred<Boolean>()
        val queuedPacket =
            QueuedPacket(
                packet = packet,
                expectedRadioSessionEpoch = expectedRadioSessionEpoch,
                expectedSourceChannelId = expectedSourceChannelId,
                dispatchCompletion = dispatchCompletion,
            )
        val admitted =
            admissionMutex.withLock {
                val capturedGeneration = outboundGeneration.value
                if (!outboundAccepting.value || queueStopped || !isExpectedSessionReady(expectedRadioSessionEpoch)) {
                    return@withLock false
                }
                responseMutex.withLock { queueResponse[packet.id] = deferred }
                queueMutex.withLock {
                    if (capturedGeneration != outboundGeneration.value) {
                        responseMutex.withLock { queueResponse.remove(packet.id) }
                        return@withLock false
                    }
                    queuedPackets.add(queuedPacket)
                    startPacketQueueLocked()
                    true
                }
            }
        if (!admitted) return false
        return try {
            dispatchCompletion.await()
        } catch (e: CancellationException) {
            val removed = queueMutex.withLock { queuedPackets.remove(queuedPacket) }
            if (removed) {
                responseMutex.withLock { queueResponse.remove(packet.id) }
                dispatchCompletion.complete(false)
            } else {
                // A dequeued worker owns the exact-session dispatch. Await its bounded per-item QueueStatus result so
                // cancellation cannot leave an orphan admin/config packet to mutate the same session later.
                withContext(NonCancellable) { dispatchCompletion.await() }
            }
            throw e
        } catch (e: Exception) {
            Logger.d { "sendToRadioAndAwait packet id=${packet.id.toUInt()} failed: ${e.message}" }
            false
        } finally {
            responseMutex.withLock { queueResponse.remove(packet.id) }
        }
    }

    private fun isExpectedSessionReady(expectedRadioSessionEpoch: Long?): Boolean = expectedRadioSessionEpoch == null ||
        radioInterfaceService.radioSessionState.value.let { session ->
            session.epoch == expectedRadioSessionEpoch && session.isConfiguredReady
        }

    override fun stopPacketQueue() {
        // Run async so callers (non-suspend) don't block, but all mutations are
        // serialized under the same mutexes used by the queue processor and senders.
        scope.launch { stopPacketQueueAndAwait() }
    }

    override suspend fun stopPacketQueueAndAwait() {
        Logger.i { "Stopping packet queueJob" }
        admissionMutex.withLock {
            // This authoritative write belongs inside the same critical section as resume. Whichever operation
            // obtains the admission lock last owns the final admission state.
            outboundAccepting.value = false
            val retiredJob =
                queueMutex.withLock {
                    queueStopped = true
                    val currentJob = queueJob
                    queueJob = null
                    queuedPackets.forEach { queued ->
                        queued.dispatchCompletion?.complete(false)
                        queued.gatewayCompletion?.complete(GatewayPacketDispatchResult.TRANSIENT_FAILURE)
                    }
                    queuedPackets.clear()
                    while (outboundChannel.tryReceive().isSuccess) {
                        // Drain every item tagged for the retired generation.
                    }
                    outboundGeneration.incrementAndGet()
                    currentJob?.cancel()
                    currentJob
                }
            responseMutex.withLock {
                queueResponse.values.forEach { if (!it.isCompleted) it.complete(false) }
                queueResponse.clear()
            }
            retiredJob?.join()
            // Status/history-log writes triggered by the retired queue are part of the same radio/database session.
            // Pause only after the queue worker is joined, then wait for every already-registered side effect.
            outboundWorkTracker.pauseAndAwaitRetiredWork()
        }
    }

    override suspend fun resumePacketQueueAndAwait() {
        admissionMutex.withLock {
            outboundWorkTracker.resume()
            queueMutex.withLock {
                queueStopped = false
                outboundAccepting.value = true
                startPacketQueueLocked()
            }
        }
    }

    internal fun tagOutbound(packet: MeshPacket): OutboundQueueItem =
        OutboundQueueItem(generation = outboundGeneration.value, packet = packet)

    private fun tagOutboundIfAccepting(packet: MeshPacket): OutboundQueueItem? {
        if (!outboundAccepting.value) return null
        val item = tagOutbound(packet)
        return item.takeIf { outboundAccepting.value && it.generation == outboundGeneration.value }
    }

    internal suspend fun admitOutboundItem(item: OutboundQueueItem) {
        queueMutex.withLock {
            if (queueStopped || !outboundAccepting.value || item.generation != outboundGeneration.value) {
                return@withLock
            }
            queuedPackets.add(
                QueuedPacket(packet = item.packet, expectedRadioSessionEpoch = null, expectedSourceChannelId = null),
            )
            startPacketQueueLocked()
        }
    }

    /** Test-only seam for deterministically ordering stop and resume at the admission boundary. */
    internal suspend fun holdOutboundAdmissionForTest(block: suspend () -> Unit) {
        admissionMutex.withLock { block() }
    }

    override fun handleQueueStatus(queueStatus: QueueStatus) {
        Logger.d { "[queueStatus] ${queueStatus.toOneLineString()}" }
        val (success, isFull, requestId) = with(queueStatus) { Triple(res == 0, free == 0, mesh_packet_id) }
        if (success && isFull) return

        ingressWorkTracker.launch(scope) {
            responseMutex.withLock {
                if (requestId != 0) {
                    queueResponse.remove(requestId)?.complete(success)
                } else {
                    queueResponse.values.firstOrNull { !it.isCompleted }?.complete(success)
                }
            }
        }
    }

    override fun removeResponse(dataRequestId: Int, complete: Boolean) {
        ingressWorkTracker.launch(scope) {
            responseMutex.withLock { queueResponse.remove(dataRequestId)?.complete(complete) }
        }
    }

    /**
     * Starts the packet queue processor. Must be called while holding [queueMutex] to ensure the check-then-start is
     * atomic — preventing two concurrent callers from launching duplicate processors.
     */
    private fun startPacketQueueLocked() {
        if (queueStopped) return
        if (queueJob?.isActive == true) return
        queueJob =
            scope.handledLaunch {
                try {
                    while (serviceRepository.connectionState.value == ConnectionState.Connected) {
                        val queuedPacket = queueMutex.withLock { queuedPackets.removeFirstOrNull() } ?: break
                        val packet = queuedPacket.packet
                        if (packet.decoded?.portnum?.value == NtsocialTransport.PRIVATE_APP_PORT_NUM) {
                            Logger.i {
                                "ntsocial_gateway_tx stage=packet_queue_dequeue packetId=${packet.id} " +
                                    "channelIndex=${packet.channel} " +
                                    "connectionState=${serviceRepository.connectionState.value}"
                            }
                        }
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            val gatewayCompletion = queuedPacket.gatewayCompletion
                            if (gatewayCompletion != null) {
                                val result = dispatchGatewayPacket(queuedPacket)
                                gatewayCompletion.complete(result)
                                continue
                            }
                            val success = processQueuedPacket(queuedPacket)
                            queuedPacket.dispatchCompletion?.complete(success)
                        } catch (e: TimeoutCancellationException) {
                            Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
                            // Clean up the deferred for this packet. sendToRadioAndAwait callers
                            // also clean up in their own finally block (idempotent remove).
                            responseMutex.withLock { queueResponse.remove(packet.id) }
                        } catch (e: CancellationException) {
                            queuedPacket.dispatchCompletion?.complete(false)
                            queuedPacket.gatewayCompletion?.complete(GatewayPacketDispatchResult.TRANSIENT_FAILURE)
                            throw e // Preserve structured concurrency cancellation propagation.
                        } catch (e: Exception) {
                            Logger.d { "queueJob packet id=${packet.id.toUInt()} failed" }
                            responseMutex.withLock { queueResponse.remove(packet.id) }
                            queuedPacket.dispatchCompletion?.complete(false)
                            queuedPacket.gatewayCompletion?.complete(GatewayPacketDispatchResult.TRANSIENT_FAILURE)
                        }
                        // Deferred cleanup is now handled in the catch blocks above.
                        // handleQueueStatus (normal success) and stopPacketQueue (bulk cleanup)
                        // also remove entries, and these removals are idempotent.
                    }
                } finally {
                    // Hold queueMutex so that clearing queueJob and the restart decision are
                    // atomic with respect to new senders calling startPacketQueueLocked().
                    queueMutex.withLock {
                        queueJob = null
                        if (!queueStopped && queuedPackets.isNotEmpty()) {
                            startPacketQueueLocked()
                        }
                    }
                }
            }
    }

    private fun changeStatus(packetId: Int, m: MessageStatus) = outboundWorkTracker.launch(scope) {
        if (packetId != 0) {
            getDataPacketById(packetId)?.let { p ->
                if (p.status == m) return@launch
                packetRepository.value.updateMessageStatus(p, m)
                serviceBroadcasts.broadcastMessageStatus(packetId, m)
            }
        }
    }

    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1.seconds) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.value.getPacketById(packetId)
            if (dataPacket == null) delay(100.milliseconds)
        }
        dataPacket
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun dispatchGatewayPacket(queuedPacket: QueuedPacket): GatewayPacketDispatchResult {
        val expectedEpoch =
            queuedPacket.expectedRadioSessionEpoch ?: return GatewayPacketDispatchResult.TRANSIENT_FAILURE
        val expectedSource =
            queuedPacket.expectedSourceChannelId ?: return GatewayPacketDispatchResult.TRANSIENT_FAILURE
        val session = radioInterfaceService.radioSessionState.value
        if (
            session.epoch != expectedEpoch ||
            !session.isConfiguredReady ||
            !gatewayIngressSessionGate.isActive(expectedEpoch)
        ) {
            return GatewayPacketDispatchResult.TRANSIENT_FAILURE
        }
        val snapshotGeneration = radioConfigRepository.channelSnapshotGeneration.value
        if (snapshotGeneration and 1L != 0L) return GatewayPacketDispatchResult.TRANSIENT_FAILURE
        val channelSet = radioConfigRepository.channelSetFlow.first()
        if (
            radioConfigRepository.channelSnapshotGeneration.value != snapshotGeneration ||
            channelSet.sourceChannelId(queuedPacket.packet.channel) != expectedSource
        ) {
            return GatewayPacketDispatchResult.SOURCE_IDENTITY_MISMATCH
        }
        val response = sendPacket(queuedPacket)
        val accepted =
            try {
                withTimeout(TIMEOUT) { response.await() }
            } catch (_: TimeoutCancellationException) {
                false
            } finally {
                responseMutex.withLock { queueResponse.remove(queuedPacket.packet.id) }
            }
        return if (accepted) {
            GatewayPacketDispatchResult.ACCEPTED
        } else {
            GatewayPacketDispatchResult.TRANSIENT_FAILURE
        }
    }

    private suspend fun processQueuedPacket(queuedPacket: QueuedPacket): Boolean {
        val packet = queuedPacket.packet
        val response = sendPacket(queuedPacket)
        return try {
            Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting" }
            withTimeout(TIMEOUT) { response.await() }
                .also { success -> Logger.d { "queueJob packet id=${packet.id.toUInt()} success $success" } }
        } catch (_: TimeoutCancellationException) {
            Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
            false
        } finally {
            responseMutex.withLock { queueResponse.remove(packet.id) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendPacket(queuedPacket: QueuedPacket): Deferred<Boolean> {
        val packet = queuedPacket.packet
        // Reuse a deferred pre-registered by sendToRadioAndAwait, or create a new one.
        val deferred = responseMutex.withLock { queueResponse.getOrPut(packet.id) { CompletableDeferred() } }
        try {
            if (serviceRepository.connectionState.value != ConnectionState.Connected) {
                throw RadioNotConnectedException()
            }
            if (packet.decoded?.portnum?.value == NtsocialTransport.PRIVATE_APP_PORT_NUM) {
                Logger.i {
                    "ntsocial_gateway_tx stage=to_radio packetId=${packet.id} channelIndex=${packet.channel} " +
                        "bytes=${packet.decoded?.payload?.size ?: 0}"
                }
            }
            val toRadio = ToRadio(packet = packet)
            val admitted =
                queuedPacket.expectedRadioSessionEpoch?.let { expectedEpoch ->
                    sendToRadioForSession(toRadio, expectedEpoch)
                }
                    ?: run {
                        sendToRadio(toRadio)
                        true
                    }
            if (!admitted) deferred.complete(false)
        } catch (ex: RadioNotConnectedException) {
            Logger.w(ex) { "sendToRadio skipped: Not connected to radio" }
            deferred.complete(false)
        } catch (ex: Exception) {
            Logger.e(ex) { "sendToRadio error: ${ex.message}" }
            deferred.complete(false)
        }
        // Return a read-only Deferred view (kotlinx.coroutines 1.11+) so callers can await it
        // without being able to complete the underlying CompletableDeferred; cancellation is
        // still exposed via Deferred/Job.
        return deferred.asDeferred()
    }

    private fun insertMeshLog(packetToSave: MeshLog) {
        outboundWorkTracker.launch(scope) {
            Logger.d { "Inserting ${packetToSave.message_type} mesh log (${packetToSave.raw_message.length} chars)" }
            meshLogRepository.value.insert(packetToSave)
        }
    }
}

internal data class OutboundQueueItem(val generation: Long, val packet: MeshPacket)

private data class QueuedPacket(
    val packet: MeshPacket,
    val expectedRadioSessionEpoch: Long?,
    val expectedSourceChannelId: String?,
    val gatewayCompletion: CompletableDeferred<GatewayPacketDispatchResult>? = null,
    val dispatchCompletion: CompletableDeferred<Boolean>? = null,
)

private fun ChannelSet.sourceChannelId(slotIndex: Int): String? {
    val settings = settings.getOrNull(slotIndex) ?: return null
    val role = if (slotIndex == 0) ProtoChannel.Role.PRIMARY else ProtoChannel.Role.SECONDARY
    return NtsocialGatewayIdentity.channel(
        ProtoChannel(index = slotIndex, role = role, settings = settings),
        lora_config ?: Config.LoRaConfig(),
    )
        .sourceChannelId
}
