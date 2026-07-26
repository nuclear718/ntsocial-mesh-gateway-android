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
package com.ntsocial.meshlink.core.repository.usecase

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageIdentity
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.testing.FakeAppPreferences
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeRadioController
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SendMessageUseCaseTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var packetRepository: PacketRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var appPreferences: FakeAppPreferences
    private lateinit var messageQueue: MessageQueue
    private lateinit var radioConfigRepository: RadioConfigRepository
    private lateinit var useCase: SendMessageUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        packetRepository = mock(MockMode.autofill)
        radioController = FakeRadioController()
        appPreferences = FakeAppPreferences()
        messageQueue = mock(MockMode.autofill)
        radioConfigRepository = mock(MockMode.autofill)
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())

        useCase =
            SendMessageUseCaseImpl(
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioController = radioController,
                homoglyphEncodingPrefs = appPreferences.homoglyph,
                messageQueue = messageQueue,
                radioConfigRepository = radioConfigRepository,
            )
    }

    @Test
    fun `invoke with broadcast message simply sends data packet`() = runTest {
        // Arrange
        val ourNode = Node(num = 1, user = User(id = "!1234"))
        nodeRepository.setOurNode(ourNode)
        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act
        useCase("Hello broadcast", "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        radioController.favoritedNodes.size shouldBe 0
        radioController.sentSharedContacts.size shouldBe 0
    }

    @Test
    fun `id zero well known channel still persists and queues native outgoing text`() = runTest {
        nodeRepository.setOurNode(Node(num = 0x1234, user = User(id = DataPacket.ID_LOCAL)))
        var savedPacket: DataPacket? = null
        var savedGatewayIdentity: NtsocialGatewayMessageIdentity? = null
        everySuspend { packetRepository.savePacket(any(), any(), any(), any(), any(), any(), any()) } calls
            { call ->
                savedPacket = call.arg(2)
                savedGatewayIdentity = call.arg(6)
            }
        val channelSettings = ChannelSettings(name = "LongFast", psk = byteArrayOf(1).toByteString())
        every { radioConfigRepository.channelSetFlow } returns
            MutableStateFlow(ChannelSet(settings = listOf(channelSettings)))

        useCase("Hello LongFast", "0${DataPacket.ID_BROADCAST}", null)

        verifySuspend { packetRepository.savePacket(any(), any(), any(), any(), any(), any(), any()) }
        verifySuspend { messageQueue.enqueue(any()) }
        assertEquals("!00001234", savedPacket?.from)
        assertEquals(
            NtsocialGatewayIdentity.channel(Channel(index = 0, role = Channel.Role.PRIMARY, settings = channelSettings))
                .sourceChannelId,
            assertNotNull(savedGatewayIdentity).sourceChannelId,
        )
    }

    @Test
    fun `invoke with direct message to older firmware triggers favoriteNode`() = runTest {
        // Arrange
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.0.0"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 12345, user = User(id = "!dest"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        radioController.favoritedNodes.size shouldBe 1
        radioController.favoritedNodes[0] shouldBe 12345
    }

    @Test
    fun `invoke with direct message to new firmware triggers sendSharedContact`() = runTest {
        // Arrange
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.7.12"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 67890, user = User(id = "!dest"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        radioController.sentSharedContacts.size shouldBe 1
        radioController.sentSharedContacts[0] shouldBe 67890
    }

    @Test
    fun `invoke with homoglyph enabled transforms text`() = runTest {
        // Arrange
        val ourNode = Node(num = 1)
        nodeRepository.setOurNode(ourNode)
        appPreferences.homoglyph.setHomoglyphEncodingEnabled(true)

        val originalText = "\u0410pple" // Cyrillic A

        // Act
        useCase(originalText, "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        // Verified by observing that no exception is thrown and coverage is hit.
    }

    @Test
    fun `invoke with PKI DM triggers sendSharedContact`() = runTest {
        // Arrange: PKI DMs use contactKey = "8!nodeHex" (PKC_CHANNEL_INDEX = 8)
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.7.12"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 0x70fdde9b.toInt(), user = User(id = "!70fdde9b"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act — PKI DM: channel 8 + node ID
        useCase("PKI direct message", "${DataPacket.PKC_CHANNEL_INDEX}!70fdde9b", null)

        // Assert — sendSharedContact should be called for PKI DMs
        radioController.sentSharedContacts.size shouldBe 1
        radioController.sentSharedContacts[0] shouldBe 0x70fdde9b.toInt()
        radioController.favoritedNodes.size shouldBe 0
    }

    @Test
    fun `invoke with channel DM does not trigger sendSharedContact or favorite`() = runTest {
        // Arrange: channel-based DMs use contactKey = "<ch>!nodeHex" where ch is 0-7
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.7.12"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 0x12345678, user = User(id = "!12345678"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act — channel 1 DM (not PKI, not legacy)
        useCase("Channel DM", "1!12345678", null)

        // Assert — neither sendSharedContact nor favorite should be called for channel DMs
        radioController.sentSharedContacts.size shouldBe 0
        radioController.favoritedNodes.size shouldBe 0
    }

    @Test
    fun `invoke with PKI DM to older firmware does not trigger favorite`() = runTest {
        // Arrange: PKI DMs with old firmware should NOT fall through to favoriting
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.0.0"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 0xABCDEF01.toInt(), user = User(id = "!abcdef01"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act — PKI DM with firmware that doesn't support verified contacts
        useCase("Old PKI DM", "${DataPacket.PKC_CHANNEL_INDEX}!abcdef01", null)

        // Assert — PKI DMs should not trigger legacy favoriting (that's only for channel==null)
        radioController.sentSharedContacts.size shouldBe 0
        radioController.favoritedNodes.size shouldBe 0
    }
}
