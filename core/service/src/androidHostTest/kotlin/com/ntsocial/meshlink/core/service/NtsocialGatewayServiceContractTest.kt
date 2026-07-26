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
package com.ntsocial.meshlink.core.service

import com.ntsocial.meshlink.core.gateway.INtsocialEnvelopeCallback
import com.ntsocial.meshlink.core.gateway.INtsocialGatewayService
import com.ntsocial.meshlink.core.gateway.NtsocialEnvelopeData
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.service.testing.FakeNtsocialGatewayService
import okio.ByteString.Companion.toByteString
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Verifies the project-owned NTsocial Gateway IPC contract without touching IMeshService. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NtsocialGatewayServiceContractTest {

    @Test
    fun `fake implementation matches NTsocial gateway aidl contract`() {
        val service: INtsocialGatewayService = FakeNtsocialGatewayService()
        var observedEnvelope: NtsocialEnvelopeData? = null

        val callback =
            object : INtsocialEnvelopeCallback.Stub() {
                override fun onNtsocialEnvelope(envelope: NtsocialEnvelopeData?) {
                    observedEnvelope = envelope
                }
            }

        assertEquals(1234, service.sendNtsocialPayload(0, byteArrayOf(0x01)))
        service.observeNtsocialEnvelope(callback)

        val status = service.gatewayStatus
        val cachedEnvelope = service.cachedNtsocialEnvelopes.single()

        assertEquals("CONNECTED", status.connectionState)
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, status.privateAppPortNum)
        assertEquals(NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM, status.legacyReceiveOnlyPortNum)
        assertEquals(NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES, status.maxPayloadSizeBytes)
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, cachedEnvelope.portNum)
        assertNotNull(observedEnvelope)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `gateway constants expose protected IPC boundary`() {
        assertEquals("com.ntsocial.meshlink.gateway.BIND", NtsocialGatewayContract.ACTION_BIND)
        assertEquals("com.ntsocial.meshlink.permission.BIND_NTSOCIAL_GATEWAY", NtsocialGatewayContract.PERMISSION_BIND)
    }

    @Test
    fun `content provider gateway contract is versioned and metadata only`() {
        val authority = NtsocialGatewayContract.authorityFor("com.ntsocial.meshlink")

        assertEquals("com.ntsocial.meshlink.gateway.COMMAND", NtsocialGatewayContract.ACTION_COMMAND)
        assertEquals("com.ntsocial.meshlink.gateway.EVENT", NtsocialGatewayContract.ACTION_EVENT)
        assertEquals(
            "content://com.ntsocial.meshlink.gateway/v1/status",
            NtsocialGatewayContract.statusUri(authority).toString(),
        )
        assertEquals(
            "content://com.ntsocial.meshlink.gateway/v1/envelopes",
            NtsocialGatewayContract.envelopesUri(authority).toString(),
        )
        assertEquals(NtsocialGatewayContract.EVENT_ENVELOPE_AVAILABLE, "ENVELOPE_AVAILABLE")
        assertEquals(NtsocialGatewayContract.COLUMN_RAW_BYTES, "raw_bytes")
    }

    @Test
    fun `gateway v2 contract is additive overlay only`() {
        val authority = NtsocialGatewayContract.authorityFor("com.ntsocial.meshlink")

        assertEquals(1, NtsocialGatewayContract.API_VERSION)
        assertEquals(2, NtsocialGatewayContract.API_VERSION_V2)
        assertEquals(
            "content://com.ntsocial.meshlink.gateway/v2/status",
            NtsocialGatewayContract.v2StatusUri(authority).toString(),
        )
        assertEquals(
            "content://com.ntsocial.meshlink.gateway/v2/channels",
            NtsocialGatewayContract.v2ChannelsUri(authority).toString(),
        )
        assertEquals(
            "content://com.ntsocial.meshlink.gateway/v2/message-changes?after=7&limit=20",
            NtsocialGatewayContract.v2MessageChangesUri(authority, after = 7, limit = 20).toString(),
        )
        assertEquals("SEND_NTSOCIAL_ENVELOPE_TO_ROUTE", NtsocialGatewayContract.COMMAND_SEND_NTSOCIAL_ENVELOPE_TO_ROUTE)
        assertEquals(32, NtsocialGatewayContract.SOURCE_MESSAGE_ID_HEX_LENGTH)
        assertEquals("source_channel_id", NtsocialGatewayContract.COLUMN_SOURCE_CHANNEL_ID)
        assertEquals("source_message_id", NtsocialGatewayContract.COLUMN_SOURCE_MESSAGE_ID)
        assertEquals("route_token", NtsocialGatewayContract.EXTRA_ROUTE_TOKEN)
    }

    @Test
    fun `mapper exposes only NTsocial envelope metadata`() {
        val headerMsgId = ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) { it.toByte() }.toByteString()
        val payload = byteArrayOf(0x01, 0x02, 0x03).toByteString()
        val cached =
            NtsocialCachedEnvelope(
                direction = NtsocialEnvelopeDirection.INBOUND,
                envelope =
                NtsocialEnvelope(
                    version = NtsocialTransport.CURRENT_VERSION,
                    headerMsgId = headerMsgId,
                    payload = payload,
                ),
                rawBytes = byteArrayOf(0x4E, 0x4D, 0x01).toByteString(),
                packetId = 77,
                from = "!12345678",
                to = "^all",
                channelIndex = 2,
                portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                cachedAtMillis = 100L,
            )

        val envelope = cached.toGatewayEnvelopeData()
        val status = buildNtsocialGatewayStatus(ConnectionState.DeviceSleep, cachedEnvelopeCount = 1)

        assertEquals("INBOUND", envelope.direction)
        assertEquals(77, envelope.packetId)
        assertEquals(2, envelope.channelIndex)
        assertContentEquals(headerMsgId.toByteArray(), envelope.headerMsgId)
        assertContentEquals(payload.toByteArray(), envelope.payload)
        assertEquals("DEVICE_SLEEP", status.connectionState)
        assertEquals(1, status.cachedEnvelopeCount)
    }
}
