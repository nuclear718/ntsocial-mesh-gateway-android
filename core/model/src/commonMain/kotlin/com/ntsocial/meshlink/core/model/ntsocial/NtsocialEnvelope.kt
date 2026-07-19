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

import okio.ByteString

/** A single NTsocial message envelope carried inside Meshtastic PRIVATE_APP raw bytes. */
data class NtsocialEnvelope(val version: Int, val headerMsgId: ByteString, val payload: ByteString) {
    val headerMsgIdHex: String = headerMsgId.hex()

    init {
        require(version == NtsocialTransport.CURRENT_VERSION) { "Unsupported NTsocial envelope version: $version" }
        require(headerMsgId.size == NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) {
            "NTsocial headerMsgId must be ${NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES} bytes"
        }
        require(payload.size in 1..NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES) {
            "NTsocial payload must be 1..${NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES} bytes"
        }
    }
}
