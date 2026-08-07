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

import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.DurableQueuedPacket
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.GatewayPacketDispatchResult
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test

class IosDurableMessageQueueTest {
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val radioController = mock<RadioController>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

    @Test
    fun `durable source mismatch is terminal and never reaches radio dispatch`() = runTest {
        val fixture = fixture()
        val replacement = ChannelSet(settings = listOf(ChannelSettings(name = "replacement")))
        fixture.channels.value = replacement
        everySuspend { packetRepository.getDurableQueuedPackets() } returns listOf(fixture.queued)

        fixture.queue.drain()

        verifySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ERROR) }
        verifySuspend(mode = VerifyMode.not) { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) }
        fixture.queue.close()
    }

    @Test
    fun `final inbound activation revision drains an already connected durable row`() = runTest {
        val fixture = fixture(activeInitially = false)
        everySuspend { packetRepository.getDurableQueuedPackets() } returns listOf(fixture.queued)
        everySuspend { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) } returns
            GatewayPacketDispatchResult.ACCEPTED

        fixture.queue.start()
        runCurrent()
        fixture.gatewayIngressSessionGate.publish(EPOCH)
        runCurrent()

        verifySuspend(mode = VerifyMode.exactly(1)) {
            commandSender.sendDataAwaitForGatewaySession(fixture.packet, EPOCH, fixture.sourceChannelId)
        }
        verifySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ENROUTE) }
        fixture.queue.close()
    }

    private fun TestScope.fixture(activeInitially: Boolean = true): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val session = MutableStateFlow(readySession())
        val gatewayIngressSessionGate = GatewayIngressSessionGate()
        if (activeInitially) gatewayIngressSessionGate.publish(EPOCH)
        val settings = ChannelSettings(name = "original")
        val channelSet = ChannelSet(settings = listOf(settings))
        val channels = MutableStateFlow(channelSet)
        val sourceChannelId =
            NtsocialGatewayIdentity.channel(
                Channel(index = 0, role = Channel.Role.PRIMARY, settings = settings),
                channelSet.lora_config ?: Config.LoRaConfig(),
            )
                .sourceChannelId
        val packet =
            DataPacket(bytes = null, dataType = 256, id = 501, status = MessageStatus.QUEUED, channel = 0, time = 1)

        every { radioController.connectionState } returns connection
        every { radioInterfaceService.radioSessionState } returns session
        every { radioConfigRepository.channelSetFlow } returns channels

        val queue =
            IosDurableMessageQueue(
                packetRepository = packetRepository,
                radioController = radioController,
                commandSender = commandSender,
                radioConfigRepository = radioConfigRepository,
                radioInterfaceService = radioInterfaceService,
                gatewayIngressSessionGate = gatewayIngressSessionGate,
                dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
            )
        return Fixture(
            queue = queue,
            packet = packet,
            queued = DurableQueuedPacket(packet, sourceChannelId),
            channels = channels,
            gatewayIngressSessionGate = gatewayIngressSessionGate,
            sourceChannelId = sourceChannelId,
        )
    }

    private fun readySession() = RadioSessionState(
        epoch = EPOCH,
        selectedDeviceAddress = RADIO,
        activeDeviceAddress = RADIO,
        transportConnectionState = ConnectionState.Connected,
        configured = true,
    )

    private data class Fixture(
        val queue: IosDurableMessageQueue,
        val packet: DataPacket,
        val queued: DurableQueuedPacket,
        val channels: MutableStateFlow<ChannelSet>,
        val gatewayIngressSessionGate: GatewayIngressSessionGate,
        val sourceChannelId: String,
    )

    private companion object {
        const val EPOCH = 7L
        const val RADIO = "xAAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
    }
}
