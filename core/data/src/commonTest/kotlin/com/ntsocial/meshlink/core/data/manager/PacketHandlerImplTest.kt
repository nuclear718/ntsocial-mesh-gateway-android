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
package com.ntsocial.meshlink.core.data.manager

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.GatewayPacketDispatchResult
import com.ntsocial.meshlink.core.repository.MeshLogRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.testing.FakeRadioConfigRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacketHandlerImplTest {

    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceBroadcasts: ServiceBroadcasts = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val meshLogRepository: MeshLogRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val radioSessionStateFlow = MutableStateFlow(readySession(epoch = 1))

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var handler: PacketHandlerImpl
    private lateinit var ingressWorkTracker: RadioIngressWorkTracker
    private lateinit var channelOperationLock: ChannelOperationLock
    private lateinit var radioConfigRepository: FakeRadioConfigRepository
    private lateinit var gatewayIngressSessionGate: GatewayIngressSessionGate

    @BeforeTest
    fun setUp() {
        radioSessionStateFlow.value = readySession(epoch = 1)
        every { serviceRepository.connectionState } returns connectionStateFlow
        every { radioInterfaceService.radioSessionState } returns radioSessionStateFlow

        ingressWorkTracker = RadioIngressWorkTracker()
        channelOperationLock = ChannelOperationLock()
        radioConfigRepository = FakeRadioConfigRepository()
        gatewayIngressSessionGate = GatewayIngressSessionGate()
        handler =
            PacketHandlerImpl(
                lazy { packetRepository },
                serviceBroadcasts,
                radioInterfaceService,
                lazy { meshLogRepository },
                serviceRepository,
                ingressWorkTracker,
                channelOperationLock,
                radioConfigRepository,
                gatewayIngressSessionGate,
                testScope,
            )
    }

    @Test
    fun testInitialization() {
        assertNotNull(handler)
    }

    @Test
    fun `sendToRadio with ToRadio sends immediately`() {
        val toRadio = ToRadio(packet = MeshPacket(id = 123))

        handler.sendToRadio(toRadio)

        verify { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `sendToRadio with MeshPacket queues and sends when connected`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 456)
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(packet)
        testScheduler.runCurrent()

        verify { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `handleQueueStatus completes deferred`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 789)
        connectionStateFlow.value = ConnectionState.Connected

        handler.sendToRadio(packet)
        testScheduler.runCurrent()

        val status =
            QueueStatus(
                mesh_packet_id = 789,
                res = 0, // Success
                free = 1,
            )

        handler.handleQueueStatus(status)
        testScheduler.runCurrent()
    }

    @Test
    fun `handleQueueStatus property test`() = runTest(testDispatcher) {
        checkAll(Arb.int(0, 10), Arb.int(0, 32), Arb.int(0, 100000)) { res, free, packetId ->
            val status = QueueStatus(res = res, free = free, mesh_packet_id = packetId)

            // Ensure it doesn't crash on any input
            handler.handleQueueStatus(status)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `routing NAK completes awaiting sender as failure`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 790)
        connectionStateFlow.value = ConnectionState.Connected
        val result = async { handler.sendToRadioAndAwait(packet) }
        testScheduler.runCurrent()

        handler.removeResponse(packet.id, complete = false)
        testScheduler.runCurrent()

        assertFalse(result.await())
    }

    @Test
    fun `retired zero-id queue status cannot complete replacement sender`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 0, res = 0, free = 1))

        val quiesce = async(start = CoroutineStart.UNDISPATCHED) { ingressWorkTracker.pauseAndAwaitRetiredWork() }
        testScheduler.runCurrent()
        quiesce.await()
        handler.stopPacketQueueAndAwait()
        ingressWorkTracker.resume()
        handler.resumePacketQueueAndAwait()

        val replacement = async { handler.sendToRadioAndAwait(MeshPacket(id = 791)) }
        testScheduler.runCurrent()
        assertFalse(replacement.isCompleted)

        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 791, res = 0, free = 1))
        testScheduler.runCurrent()

        assertTrue(replacement.await())
        handler.stopPacketQueueAndAwait()
    }

    @Test
    fun `stop drains buffered fire-and-forget packet before replacement radio resumes`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.sendToRadio(MeshPacket(id = 800))

        handler.stopPacketQueueAndAwait()
        handler.resumePacketQueueAndAwait()
        testScheduler.runCurrent()

        verify(mode = VerifyMode.not) { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `already dequeued retired generation is rejected after replacement resume`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val alreadyDequeued = handler.tagOutbound(MeshPacket(id = 801))

        handler.stopPacketQueueAndAwait()
        handler.resumePacketQueueAndAwait()
        handler.admitOutboundItem(alreadyDequeued)
        testScheduler.runCurrent()

        verify(mode = VerifyMode.not) { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `outbound admission stays closed throughout radio switch`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        handler.stopPacketQueueAndAwait()

        handler.sendToRadio(MeshPacket(id = 802))
        val awaited = handler.sendToRadioAndAwait(MeshPacket(id = 803))
        assertFalse(awaited)

        handler.resumePacketQueueAndAwait()
        testScheduler.runCurrent()
        verify(mode = VerifyMode.not) { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `retired session cannot admit after replacement generation resumes`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val retiredEpoch = radioSessionStateFlow.value.epoch
        assertTrue(radioSessionStateFlow.value.isConfiguredReady)

        handler.stopPacketQueueAndAwait()
        radioSessionStateFlow.value = readySession(epoch = retiredEpoch + 1)
        handler.resumePacketQueueAndAwait()

        assertFalse(handler.sendToRadioAndAwaitForSession(MeshPacket(id = 806), retiredEpoch))
        testScheduler.runCurrent()
        verify(mode = VerifyMode.not) { radioInterfaceService.sendToRadio(any()) }
    }

    @Test
    fun `queued exact packet retains epoch and cannot dispatch through replacement transport`() =
        runTest(testDispatcher) {
            connectionStateFlow.value = ConnectionState.Connected
            val retiredEpoch = radioSessionStateFlow.value.epoch
            every { radioInterfaceService.sendToRadioForSession(any(), retiredEpoch) } calls
                {
                    radioSessionStateFlow.value = readySession(epoch = retiredEpoch + 1)
                    false
                }

            val accepted = async { handler.sendToRadioAndAwaitForSession(MeshPacket(id = 807), retiredEpoch) }
            testScheduler.runCurrent()

            assertFalse(accepted.await())
            verify(mode = VerifyMode.exactly(1)) { radioInterfaceService.sendToRadioForSession(any(), retiredEpoch) }
            verify(mode = VerifyMode.not) { radioInterfaceService.sendToRadio(any()) }
        }

    @Test
    fun `cancelled awaited packet behind queue head is withdrawn before radio dispatch`() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected
        val epoch = radioSessionStateFlow.value.epoch
        every { radioInterfaceService.sendToRadioForSession(any(), epoch) } returns true

        val head = async { handler.sendToRadioAndAwaitForSession(MeshPacket(id = 808), epoch) }
        testScheduler.runCurrent()
        val cancelled = async { handler.sendToRadioAndAwaitForSession(MeshPacket(id = 809), epoch) }
        testScheduler.runCurrent()

        cancelled.cancel()
        testScheduler.runCurrent()
        handler.handleQueueStatus(QueueStatus(mesh_packet_id = 808, res = 0, free = 1))
        testScheduler.runCurrent()

        assertTrue(head.await())
        verify(mode = VerifyMode.exactly(1)) { radioInterfaceService.sendToRadioForSession(any(), epoch) }
    }

    @Test
    fun `gateway packet revalidates durable source identity after waiting behind queue head`() =
        runTest(testDispatcher) {
            connectionStateFlow.value = ConnectionState.Connected
            val epoch = radioSessionStateFlow.value.epoch
            val originalSettings = ChannelSettings(name = "original")
            val originalSet = ChannelSet(settings = listOf(originalSettings))
            val originalSource =
                NtsocialGatewayIdentity.channel(
                    Channel(index = 0, role = Channel.Role.PRIMARY, settings = originalSettings),
                    originalSet.lora_config ?: Config.LoRaConfig(),
                )
                    .sourceChannelId
            radioConfigRepository.setCompleteChannelReadback(originalSet)
            gatewayIngressSessionGate.publish(epoch)
            every { radioInterfaceService.sendToRadioForSession(any(), epoch) } returns true

            val head = async { handler.sendToRadioAndAwaitForSession(MeshPacket(id = 810), epoch) }
            testScheduler.runCurrent()
            val gateway = async {
                handler.dispatchGatewayPacketAndAwait(MeshPacket(id = 811, channel = 0), epoch, originalSource)
            }
            testScheduler.runCurrent()

            radioConfigRepository.setCompleteChannelReadback(
                ChannelSet(settings = listOf(ChannelSettings(name = "replacement"))),
            )
            handler.handleQueueStatus(QueueStatus(mesh_packet_id = 810, res = 0, free = 1))
            testScheduler.runCurrent()

            assertTrue(head.await())
            assertTrue(gateway.await() == GatewayPacketDispatchResult.SOURCE_IDENTITY_MISMATCH)
            verify(mode = VerifyMode.exactly(1)) { radioInterfaceService.sendToRadioForSession(any(), epoch) }
        }

    @Test
    fun `stop wins admission state when queued behind concurrent resume`() = runTest(testDispatcher) {
        val lockHeld = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()
        val holder =
            launch(start = CoroutineStart.UNDISPATCHED) {
                handler.holdOutboundAdmissionForTest {
                    lockHeld.complete(Unit)
                    releaseLock.await()
                }
            }
        lockHeld.await()

        val resume = launch { handler.resumePacketQueueAndAwait() }
        testScheduler.runCurrent()
        val stop = launch { handler.stopPacketQueueAndAwait() }
        testScheduler.runCurrent()

        releaseLock.complete(Unit)
        holder.join()
        resume.join()
        stop.join()

        assertFalse(handler.sendToRadioAndAwait(MeshPacket(id = 804)))
    }

    @Test
    fun `stop awaits retired outbound status and mesh log writes before database switch`() = runTest(testDispatcher) {
        val statusStarted = CompletableDeferred<Unit>()
        val logStarted = CompletableDeferred<Unit>()
        val releaseWrites = CompletableDeferred<Unit>()
        val persisted =
            com.ntsocial.meshlink.core.model.DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 805,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.getPacketById(805) } returns persisted
        everySuspend { packetRepository.updateMessageStatus(any(), MessageStatus.ENROUTE) } calls
            {
                statusStarted.complete(Unit)
                releaseWrites.await()
            }
        everySuspend { meshLogRepository.insert(any()) } calls
            {
                logStarted.complete(Unit)
                releaseWrites.await()
            }
        val packet = MeshPacket(id = 805, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))

        handler.sendToRadio(ToRadio(packet = packet))
        testScheduler.runCurrent()
        statusStarted.await()
        logStarted.await()

        val stop = async { handler.stopPacketQueueAndAwait() }
        testScheduler.runCurrent()
        assertFalse(stop.isCompleted)

        releaseWrites.complete(Unit)
        stop.await()
        verifySuspend { packetRepository.updateMessageStatus(persisted, MessageStatus.ENROUTE) }
        verifySuspend { meshLogRepository.insert(any()) }
    }

    @Test
    fun `outgoing packets are logged with NODE_NUM_LOCAL`() = runTest(testDispatcher) {
        val packet = MeshPacket(id = 123, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP))
        val toRadio = ToRadio(packet = packet)

        handler.sendToRadio(toRadio)
        testScheduler.runCurrent()

        verifySuspend { meshLogRepository.insert(any()) }
    }

    private fun readySession(epoch: Long): RadioSessionState = RadioSessionState(
        epoch = epoch,
        selectedDeviceAddress = "same-radio",
        activeDeviceAddress = "same-radio",
        transportConnectionState = ConnectionState.Connected,
        configured = true,
    )
}
