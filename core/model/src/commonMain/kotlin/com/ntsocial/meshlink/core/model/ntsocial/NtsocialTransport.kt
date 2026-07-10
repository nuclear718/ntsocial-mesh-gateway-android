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
@file:Suppress("MagicNumber")

package com.ntsocial.meshlink.core.model.ntsocial

/** NTsocial overlay transport constants for Meshtastic private application packets. */
object NtsocialTransport {
    const val PRIVATE_APP_PORT_NUM = 256
    const val LEGACY_RECEIVE_ONLY_PORT_NUM = 497

    const val CURRENT_VERSION = 1
    const val MAGIC_SIZE_BYTES = 2
    const val VERSION_SIZE_BYTES = 1
    const val HEADER_MSG_ID_SIZE_BYTES = 16
    const val HEADER_SIZE_BYTES = MAGIC_SIZE_BYTES + VERSION_SIZE_BYTES + HEADER_MSG_ID_SIZE_BYTES

    const val MAX_ENVELOPE_SIZE_BYTES = 200
    const val MAX_PAYLOAD_SIZE_BYTES = MAX_ENVELOPE_SIZE_BYTES - HEADER_SIZE_BYTES

    /** Maximum complete NM envelope accepted from the external NTsocial application command boundary. */
    const val MAX_CLIENT_ENVELOPE_SIZE_BYTES = 180

    /** In-memory cache capacity. The gateway never writes this cache to Room in the MVP boundary. */
    const val MAX_CACHED_ENVELOPES = 128

    fun isInboundPort(portNum: Int): Boolean =
        portNum == PRIVATE_APP_PORT_NUM || portNum == LEGACY_RECEIVE_ONLY_PORT_NUM

    fun isOutboundPort(portNum: Int): Boolean = portNum == PRIVATE_APP_PORT_NUM
}
