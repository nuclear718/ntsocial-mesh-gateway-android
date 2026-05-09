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
package com.ntsocial.meshlink.core.service.testing

import com.ntsocial.meshlink.core.gateway.INtsocialEnvelopeCallback
import com.ntsocial.meshlink.core.gateway.INtsocialGatewayService
import com.ntsocial.meshlink.core.gateway.NtsocialEnvelopeData
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport

/** Contract fake for clients that bind to the protected NTsocial Gateway IPC. */
open class FakeNtsocialGatewayService : INtsocialGatewayService.Stub() {
    private val cachedEnvelope =
        NtsocialEnvelopeData(
            direction = "INBOUND",
            version = NtsocialTransport.CURRENT_VERSION,
            headerMsgId = ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) { it.toByte() },
            payload = byteArrayOf(0x01, 0x02),
            rawBytes = byteArrayOf(0x4E, 0x4D, NtsocialTransport.CURRENT_VERSION.toByte(), 0x01, 0x02),
            packetId = 1234,
            from = "!12345678",
            to = "^all",
            channelIndex = 0,
            portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM,
            cachedAtMillis = 1L,
        )

    override fun sendNtsocialPayload(channelIndex: Int, payload: ByteArray?): Int = 1234

    override fun observeNtsocialEnvelope(callback: INtsocialEnvelopeCallback?) {
        callback?.onNtsocialEnvelope(cachedEnvelope)
    }

    override fun stopObservingNtsocialEnvelope(callback: INtsocialEnvelopeCallback?) = Unit

    override fun getGatewayStatus(): NtsocialGatewayStatus = NtsocialGatewayStatus(
        connectionState = "CONNECTED",
        cachedEnvelopeCount = 1,
        privateAppPortNum = NtsocialTransport.PRIVATE_APP_PORT_NUM,
        legacyReceiveOnlyPortNum = NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM,
        maxEnvelopeSizeBytes = NtsocialTransport.MAX_ENVELOPE_SIZE_BYTES,
        maxPayloadSizeBytes = NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES,
    )

    override fun getCachedNtsocialEnvelopes(): List<NtsocialEnvelopeData> = listOf(cachedEnvelope)
}
