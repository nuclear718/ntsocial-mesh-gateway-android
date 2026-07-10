/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.core.data.repository

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NtsocialGatewayRepositoryImplTest {

    private val commandSender = RecordingCommandSender()
    private val repository = NtsocialGatewayRepositoryImpl(commandSender, CoroutineScope(SupervisorJob()))

    @Test
    fun `cacheInbound caches valid PRIVATE_APP NTsocial envelope`() {
        val payload = "inbound".encodeToByteArray().toByteString()
        val raw = NtsocialEnvelopeCodec.encode(headerMsgId = testHeaderMsgId(), payload = payload)
        val packet = MeshPacket(id = 42)
        val dataPacket = dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, raw = raw)

        val accepted = repository.cacheInbound(packet, dataPacket)

        val cached = repository.cachedEnvelopes.value.single()
        assertTrue(accepted)
        assertEquals(NtsocialEnvelopeDirection.INBOUND, cached.direction)
        assertEquals(payload, cached.envelope.payload)
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, cached.portNum)
        assertEquals(42, cached.packetId)
    }

    @Test
    fun `cacheInbound rejects non NTsocial PRIVATE_APP payload`() {
        val dataPacket =
            dataPacket(
                portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                raw = "not-nm".encodeToByteArray().toByteString(),
            )

        val accepted = repository.cacheInbound(MeshPacket(id = 42), dataPacket)

        assertFalse(accepted)
        assertTrue(repository.cachedEnvelopes.value.isEmpty())
    }

    @Test
    fun `cacheInbound accepts legacy port as receive-only compatibility`() {
        val payload = "legacy".encodeToByteArray().toByteString()
        val raw = NtsocialEnvelopeCodec.encode(headerMsgId = testHeaderMsgId(), payload = payload)
        val dataPacket = dataPacket(portNum = NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM, raw = raw)

        val accepted = repository.cacheInbound(MeshPacket(id = 43), dataPacket)

        val cached = repository.cachedEnvelopes.value.single()
        assertTrue(accepted)
        assertEquals(NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM, cached.portNum)
        assertEquals(payload, cached.envelope.payload)
    }

    @Test
    fun `cacheInbound deduplicates envelopes by header message id`() {
        val headerMsgId = testHeaderMsgId()
        val firstRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = headerMsgId,
                payload = "first".encodeToByteArray().toByteString(),
            )
        val secondRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = headerMsgId,
                payload = "second".encodeToByteArray().toByteString(),
            )

        repository.cacheInbound(
            MeshPacket(id = 1),
            dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, firstRaw),
        )
        repository.cacheInbound(
            MeshPacket(id = 2),
            dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, secondRaw),
        )

        val cached = repository.cachedEnvelopes.value.single()
        assertEquals("first".encodeToByteArray().toByteString(), cached.envelope.payload)
        assertEquals(1, repository.cachedEnvelopes.value.size)
    }

    @Test
    fun `sendTestPayload sends only PRIVATE_APP port 256 and caches outbound envelope`() {
        val headerMsgId = testHeaderMsgId()
        val payload = "outbound".encodeToByteArray().toByteString()

        val cached =
            repository.sendTestPayload(
                payload = payload,
                to = "!0000002a",
                channelIndex = 2,
                wantAck = false,
                headerMsgId = headerMsgId,
            )

        val sent = commandSender.sentData.single()
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, sent.dataType)
        assertTrue(NtsocialTransport.isOutboundPort(sent.dataType))
        assertFalse(NtsocialTransport.isOutboundPort(NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM))
        assertEquals(0, commandSender.adminSendCount)
        assertEquals(2, sent.channel)
        assertFalse(sent.wantAck)
        assertEquals(cached.rawBytes, sent.bytes)
        assertEquals(NtsocialEnvelopeDirection.OUTBOUND, cached.direction)
        assertEquals(payload, cached.envelope.payload)
        assertEquals(cached, repository.cachedEnvelopes.value.single())
    }

    @Test
    fun `sendTestPayload rejects payloads over conservative MVP limit`() {
        val payload = ByteArray(NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES + 1) { 0x01 }.toByteString()

        assertFailsWith<IllegalArgumentException> {
            repository.sendTestPayload(payload = payload, headerMsgId = testHeaderMsgId())
        }
        assertTrue(commandSender.sentData.isEmpty())
        assertTrue(repository.cachedEnvelopes.value.isEmpty())
    }

    @Test
    fun `sendRawEnvelope keeps parent NM envelope unchanged on PRIVATE_APP port`() {
        val raw = NtsocialEnvelopeCodec.encode(testHeaderMsgId(), "parent-payload".encodeToByteArray().toByteString())

        val cached =
            repository.sendRawEnvelope(
                rawEnvelope = raw,
                to = "!0000002a",
                channelIndex = 2,
                hopLimit = 3,
                wantAck = false,
            )

        val sent = commandSender.sentData.single()
        assertEquals(raw, sent.bytes)
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, sent.dataType)
        assertEquals(2, sent.channel)
        assertEquals(3, sent.hopLimit)
        assertFalse(sent.wantAck)
        assertEquals(raw, cached.rawBytes)
        assertEquals(NtsocialEnvelopeDirection.OUTBOUND, cached.direction)
    }

    @Test
    fun `sendRawEnvelope rejects invalid or oversized complete parent envelopes`() {
        assertFailsWith<IllegalArgumentException> {
            repository.sendRawEnvelope(
                rawEnvelope = "not-an-nm-envelope".encodeToByteArray().toByteString(),
                channelIndex = 0,
            )
        }

        val oversizedRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = ByteArray(NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES).toByteString(),
            )
        assertFailsWith<IllegalArgumentException> {
            repository.sendRawEnvelope(rawEnvelope = oversizedRaw, channelIndex = 0)
        }
        assertTrue(commandSender.sentData.isEmpty())
    }

    private fun dataPacket(portNum: Int, raw: ByteString) = DataPacket(
        from = "!00000001",
        to = DataPacket.ID_BROADCAST,
        bytes = raw,
        dataType = portNum,
        id = 42,
        channel = 1,
    )

    private fun testHeaderMsgId() =
        ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) { index -> (index + 1).toByte() }.toByteString()

    private class RecordingCommandSender : CommandSender {
        val sentData = mutableListOf<DataPacket>()
        var adminSendCount = 0
        private var nextPacketId = 100

        override fun getCurrentPacketId(): Long = nextPacketId.toLong()

        override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

        override fun getCachedChannelSet(): ChannelSet = ChannelSet()

        override fun generatePacketId(): Int = nextPacketId++

        override fun sendData(p: DataPacket) {
            sentData += p.copy()
        }

        override fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) {
            adminSendCount++
        }

        override suspend fun sendAdminAwait(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean {
            adminSendCount++
            return false
        }

        override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) = Unit

        override fun requestPosition(destNum: Int, currentPosition: Position) = Unit

        override fun setFixedPosition(destNum: Int, pos: Position) = Unit

        override fun requestUserInfo(destNum: Int) = Unit

        override fun requestTraceroute(requestId: Int, destNum: Int) = Unit

        override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) = Unit

        override fun requestNeighborInfo(requestId: Int, destNum: Int) = Unit
    }
}
