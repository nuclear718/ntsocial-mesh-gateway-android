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
package com.ntsocial.meshlink.core.model.ntsocial

import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NtsocialEnvelopeCodecTest {

    @Test
    fun `encode and decode round trips valid NM envelope`() {
        val headerMsgId = testHeaderMsgId()
        val payload = "hello ntsocial".encodeToByteArray().toByteString()

        val raw = NtsocialEnvelopeCodec.encode(headerMsgId = headerMsgId, payload = payload)
        val envelope = NtsocialEnvelopeCodec.decode(raw)

        assertEquals(NtsocialTransport.HEADER_SIZE_BYTES + payload.size, raw.size)
        assertEquals(NtsocialTransport.CURRENT_VERSION, envelope?.version)
        assertEquals(headerMsgId, envelope?.headerMsgId)
        assertEquals(payload, envelope?.payload)
    }

    @Test
    fun `decode rejects non NM magic`() {
        val raw =
            NtsocialEnvelopeCodec.encode(testHeaderMsgId(), "hello".encodeToByteArray().toByteString())
                .toByteArray()
                .also { it[0] = 0x00 }
                .toByteString()

        assertNull(NtsocialEnvelopeCodec.decode(raw))
    }

    @Test
    fun `decode rejects unsupported version`() {
        val raw =
            NtsocialEnvelopeCodec.encode(testHeaderMsgId(), "hello".encodeToByteArray().toByteString())
                .toByteArray()
                .also { it[2] = 0x02 }
                .toByteString()

        assertNull(NtsocialEnvelopeCodec.decode(raw))
    }

    @Test
    fun `decode rejects envelope with empty payload`() {
        val raw =
            ByteArray(NtsocialTransport.HEADER_SIZE_BYTES) { index ->
                when (index) {
                    0 -> 0x4e
                    1 -> 0x4d
                    2 -> NtsocialTransport.CURRENT_VERSION
                    else -> index
                }.toByte()
            }
                .toByteString()

        assertNull(NtsocialEnvelopeCodec.decode(raw))
    }

    @Test
    fun `encode rejects payload over conservative MVP limit`() {
        val payload = ByteArray(NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES + 1) { 0x01 }.toByteString()

        assertFailsWith<IllegalArgumentException> {
            NtsocialEnvelopeCodec.encode(headerMsgId = testHeaderMsgId(), payload = payload)
        }
    }

    private fun testHeaderMsgId() =
        ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) { index -> (index + 1).toByte() }.toByteString()
}
