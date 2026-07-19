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
@file:Suppress("MagicNumber")

package com.ntsocial.meshlink.core.model.ntsocial

import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Codec for the NTsocial MVP envelope: `NM + version + 16-byte headerMsgId + payload`. */
object NtsocialEnvelopeCodec {
    private const val BYTE_MASK = 0xff
    private const val MAGIC_N = 0x4e.toByte()
    private const val MAGIC_M = 0x4d.toByte()

    fun encode(
        headerMsgId: ByteString,
        payload: ByteString,
        version: Int = NtsocialTransport.CURRENT_VERSION,
    ): ByteString {
        val envelope = NtsocialEnvelope(version = version, headerMsgId = headerMsgId, payload = payload)
        val bytes = ByteArray(NtsocialTransport.HEADER_SIZE_BYTES + envelope.payload.size)
        bytes[0] = MAGIC_N
        bytes[1] = MAGIC_M
        bytes[2] = envelope.version.toByte()
        envelope.headerMsgId.toByteArray().copyInto(bytes, destinationOffset = VERSION_OFFSET + 1)
        envelope.payload.toByteArray().copyInto(bytes, destinationOffset = NtsocialTransport.HEADER_SIZE_BYTES)
        return bytes.toByteString()
    }

    fun decode(rawBytes: ByteString?): NtsocialEnvelope? = rawBytes
        ?.takeIf { it.size in MIN_ENVELOPE_SIZE_BYTES..NtsocialTransport.MAX_ENVELOPE_SIZE_BYTES }
        ?.takeIf { it[0] == MAGIC_N && it[1] == MAGIC_M }
        ?.takeIf { (it[VERSION_OFFSET].toInt() and BYTE_MASK) == NtsocialTransport.CURRENT_VERSION }
        ?.let { raw ->
            NtsocialEnvelope(
                version = raw[VERSION_OFFSET].toInt() and BYTE_MASK,
                headerMsgId =
                raw.substring(beginIndex = VERSION_OFFSET + 1, endIndex = NtsocialTransport.HEADER_SIZE_BYTES),
                payload = raw.substring(beginIndex = NtsocialTransport.HEADER_SIZE_BYTES),
            )
        }

    private const val VERSION_OFFSET = 2
    private const val MIN_ENVELOPE_SIZE_BYTES = NtsocialTransport.HEADER_SIZE_BYTES + 1
}
