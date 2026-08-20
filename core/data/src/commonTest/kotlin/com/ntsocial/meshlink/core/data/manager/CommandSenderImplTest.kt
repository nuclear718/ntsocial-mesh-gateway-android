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
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.NeighborInfoHandler
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import com.ntsocial.meshlink.core.repository.SessionManager
import com.ntsocial.meshlink.core.repository.TracerouteHandler
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.proto.Position as ProtoPosition

class CommandSenderImplTest {
    @Test
    fun `position request contains no local coordinates and uses explicit channel`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        var sentPacket: MeshPacket? = null

        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { packetHandler.sendToRadio(any<MeshPacket>()) } calls { call -> sentPacket = call.arg(0) }

        val sender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill),
                neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill),
                sessionManager = mock<SessionManager>(MockMode.autofill),
                radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill),
                channelOperationLock = ChannelOperationLock(),
                scope = backgroundScope,
            )

        sender.requestPositionOnChannel(
            destNum = 456,
            currentPosition = Position(latitude = 25.1234567, longitude = 121.7654321, altitude = 42),
            channelIndex = 4,
        )

        val packet = assertNotNull(sentPacket)
        assertEquals(456, packet.to)
        assertEquals(4, packet.channel)
        assertEquals(PortNum.POSITION_APP, packet.decoded?.portnum)
        assertEquals(true, packet.decoded?.want_response)
        val request = ProtoPosition.ADAPTER.decode(assertNotNull(packet.decoded).payload.toByteArray())
        assertNull(request.latitude_i)
        assertNull(request.longitude_i)
        assertNull(request.altitude)
        assertTrue(request.time > 0)
    }

    @Test
    fun `phone position uses exact self session and updates local node only after acceptance`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
        val radioSessionState = MutableStateFlow(readySession(epoch = 42))
        val myNodeNum = MutableStateFlow<Int?>(123)
        var sentPacket: MeshPacket? = null
        var sentEpoch: Long? = null

        every { nodeManager.myNodeNum } returns myNodeNum
        every { nodeManager.handleReceivedPosition(any(), any(), any(), any()) } returns Unit
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioInterfaceService.radioSessionState } returns radioSessionState
        stubAtomicSessionProjection(radioInterfaceService, radioSessionState)
        everySuspend { packetHandler.sendToRadioAndAwaitForSession(any(), any()) } calls
            { call ->
                sentPacket = call.arg(0)
                sentEpoch = call.arg(1)
                true
            }

        val sender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill),
                neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill),
                sessionManager = mock<SessionManager>(MockMode.autofill),
                radioInterfaceService = radioInterfaceService,
                channelOperationLock = ChannelOperationLock(),
                scope = backgroundScope,
            )
        val position = ProtoPosition(latitude_i = 250_123_456, longitude_i = 1_210_654_321)

        assertTrue(sender.sendPhonePositionForSession(position, expectedNodeNum = 123, expectedRadioSessionEpoch = 42))

        val packet = assertNotNull(sentPacket)
        assertEquals(123, packet.to)
        assertEquals(0, packet.channel)
        assertEquals(PortNum.POSITION_APP, packet.decoded?.portnum)
        assertEquals(false, packet.decoded?.want_response)
        assertEquals(position, ProtoPosition.ADAPTER.decode(assertNotNull(packet.decoded).payload.toByteArray()))
        assertEquals(42L, sentEpoch)
        verify { nodeManager.handleReceivedPosition(123, 123, position, any()) }
    }

    @Test
    fun `rejected exact phone position never updates the local node database`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        val radioSessionState = MutableStateFlow(readySession(epoch = 42))
        every { radioInterfaceService.radioSessionState } returns radioSessionState
        stubAtomicSessionProjection(radioInterfaceService, radioSessionState)
        everySuspend { packetHandler.sendToRadioAndAwaitForSession(any(), any()) } returns false

        val sender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill),
                neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill),
                sessionManager = mock<SessionManager>(MockMode.autofill),
                radioInterfaceService = radioInterfaceService,
                channelOperationLock = ChannelOperationLock(),
                scope = backgroundScope,
            )

        assertEquals(
            false,
            sender.sendPhonePositionForSession(
                ProtoPosition(latitude_i = 250_123_456, longitude_i = 1_210_654_321),
                expectedNodeNum = 123,
                expectedRadioSessionEpoch = 42,
            ),
        )
        verify(mode = VerifyMode.not) { nodeManager.handleReceivedPosition(any(), any(), any(), any()) }
    }

    @Test
    fun `exact phone position keeps same-node replacement behind the database projection`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
        val radioSessionState = MutableStateFlow(readySession(epoch = 42))
        val channelOperationLock = ChannelOperationLock()
        val releaseSend = CompletableDeferred<Unit>()
        var activeDatabase = "retired"
        var updatedDatabase: String? = null

        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.handleReceivedPosition(any(), any(), any(), any()) } calls
            {
                updatedDatabase = activeDatabase
            }
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioInterfaceService.radioSessionState } returns radioSessionState
        stubAtomicSessionProjection(radioInterfaceService, radioSessionState)
        everySuspend { packetHandler.sendToRadioAndAwaitForSession(any(), any()) } calls
            {
                releaseSend.await()
                true
            }
        val sender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill),
                neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill),
                sessionManager = mock<SessionManager>(MockMode.autofill),
                radioInterfaceService = radioInterfaceService,
                channelOperationLock = channelOperationLock,
                scope = backgroundScope,
            )
        val position = ProtoPosition(latitude_i = 250_123_456, longitude_i = 1_210_654_321)

        val send =
            async(start = CoroutineStart.UNDISPATCHED) {
                sender.sendPhonePositionForSession(position, expectedNodeNum = 123, expectedRadioSessionEpoch = 42)
            }
        val replacement = async {
            channelOperationLock.withLock {
                activeDatabase = "replacement"
                radioSessionState.value = readySession(epoch = 43)
            }
        }
        runCurrent()

        assertFalse(replacement.isCompleted)
        releaseSend.complete(Unit)
        assertTrue(send.await())
        replacement.await()
        assertEquals("retired", updatedDatabase)
        assertEquals(43L, radioSessionState.value.epoch)
    }

    @Test
    fun `same-node environment reconnect after exact acceptance skips local database projection`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
        val radioSessionState = MutableStateFlow(readySession(epoch = 42))

        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioInterfaceService.radioSessionState } returns radioSessionState
        every { radioInterfaceService.runIfCurrentRadioSession(any(), any()) } calls
            {
                radioSessionState.value = readySession(epoch = 43)
                false
            }
        everySuspend { packetHandler.sendToRadioAndAwaitForSession(any(), any()) } returns true
        val sender =
            CommandSenderImpl(
                packetHandler = packetHandler,
                nodeManager = nodeManager,
                radioConfigRepository = radioConfigRepository,
                tracerouteHandler = mock<TracerouteHandler>(MockMode.autofill),
                neighborInfoHandler = mock<NeighborInfoHandler>(MockMode.autofill),
                sessionManager = mock<SessionManager>(MockMode.autofill),
                radioInterfaceService = radioInterfaceService,
                channelOperationLock = ChannelOperationLock(),
                scope = backgroundScope,
            )

        assertFalse(
            sender.sendPhonePositionForSession(
                ProtoPosition(latitude_i = 250_123_456, longitude_i = 1_210_654_321),
                expectedNodeNum = 123,
                expectedRadioSessionEpoch = 42,
            ),
        )
        verify(mode = VerifyMode.not) { nodeManager.handleReceivedPosition(any(), any(), any(), any()) }
    }

    private fun stubAtomicSessionProjection(
        radioInterfaceService: RadioInterfaceService,
        radioSessionState: MutableStateFlow<RadioSessionState>,
    ) {
        every { radioInterfaceService.runIfCurrentRadioSession(any(), any()) } calls
            { call ->
                val expectedEpoch = call.arg<Long>(0)
                val session = radioSessionState.value
                if (session.epoch == expectedEpoch && session.isConfiguredReady) {
                    call.arg<() -> Unit>(1).invoke()
                    true
                } else {
                    false
                }
            }
    }

    private companion object {
        private const val RADIO_ADDRESS = "xAA:BB:CC:DD:EE:FF"

        private fun readySession(epoch: Long): RadioSessionState = RadioSessionState(
            epoch = epoch,
            selectedDeviceAddress = RADIO_ADDRESS,
            activeDeviceAddress = RADIO_ADDRESS,
            transportConnectionState = ConnectionState.Connected,
            configured = true,
        )
    }
}
