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
package com.ntsocial.meshlink.core.gateway.apple

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/** Canonical, length-delimited authentication and idempotency encoding. */
object AppleGatewayCommandCodec {
    private val COMMAND_DOMAIN = "ntsocial-apple-gateway-command-v1".encodeUtf8()
    private val FINGERPRINT_DOMAIN = "ntsocial-apple-gateway-fingerprint-v1".encodeUtf8()

    fun canonicalAuthenticationBytes(command: AppleGatewayCommand): ByteString = Buffer()
        .writePart(COMMAND_DOMAIN)
        .writeInt(command.schemaVersion)
        .writePart(command.commandTypeWireValue().encodeUtf8())
        .writePart(command.callerId.encodeUtf8())
        .writePart(command.requestId.encodeUtf8())
        .writePart(command.clientMessageId.uppercase().encodeUtf8())
        .writePart(command.sourceChannelId.encodeUtf8())
        .writePart(command.routeToken.encodeUtf8())
        .writePart(command.radioGeneration.encodeUtf8())
        .writeLong(command.issuedAtMillis)
        .writeLong(command.expiresAtMillis)
        .writeInt(command.keyVersion)
        .writePart(command.nonce)
        .writeBody(command.body)
        .readByteString()

    fun requestFingerprint(command: AppleGatewayCommand): String = Buffer()
        .writePart(FINGERPRINT_DOMAIN)
        .writePart(command.commandTypeWireValue().encodeUtf8())
        .writePart(command.sourceChannelId.encodeUtf8())
        .writeBody(command.body)
        .readByteString()
        .sha256()
        .hex()

    private fun AppleGatewayCommand.commandTypeWireValue(): String = when (body) {
        is AppleGatewayCommandBody.NtsocialEnvelope -> "SEND_NTSOCIAL_ENVELOPE_TO_ROUTE"
        is AppleGatewayCommandBody.NativeBroadcastText -> "SEND_CHANNEL_TEXT"
    }

    private fun Buffer.writeBody(body: AppleGatewayCommandBody): Buffer = apply {
        when (body) {
            is AppleGatewayCommandBody.NtsocialEnvelope -> {
                writePart(body.rawEnvelope)
                writeNullablePart(body.destination?.encodeUtf8())
                writeInt(body.hopLimit)
                writeByte(if (body.wantAck) 1 else 0)
            }

            is AppleGatewayCommandBody.NativeBroadcastText -> writePart(body.text.encodeUtf8())
        }
    }

    private fun Buffer.writeNullablePart(value: ByteString?): Buffer = apply {
        if (value == null) {
            writeInt(-1)
        } else {
            writePart(value)
        }
    }

    private fun Buffer.writePart(value: ByteString): Buffer = apply {
        writeInt(value.size)
        write(value)
    }
}

object AppleGatewayAuthenticator {
    fun tag(command: AppleGatewayCommand, key: ByteString): ByteString {
        require(key.size == AppleGatewayContract.AUTHENTICATION_KEY_SIZE_BYTES) {
            "Apple Gateway authentication key must be ${AppleGatewayContract.AUTHENTICATION_KEY_SIZE_BYTES} bytes"
        }
        return AppleGatewayCommandCodec.canonicalAuthenticationBytes(command).hmacSha256(key)
    }

    fun verify(command: AppleGatewayCommand, key: ByteString): Boolean =
        key.size == AppleGatewayContract.AUTHENTICATION_KEY_SIZE_BYTES &&
            command.authenticationTag.size == AppleGatewayContract.AUTHENTICATION_TAG_SIZE_BYTES &&
            constantTimeEquals(tag(command, key), command.authenticationTag)

    internal fun constantTimeEquals(expected: ByteString, actual: ByteString): Boolean {
        var difference = expected.size xor actual.size
        val sharedSize = minOf(expected.size, actual.size)
        for (index in 0 until sharedSize) {
            difference = difference or (expected[index].toInt() xor actual[index].toInt())
        }
        return difference == 0
    }
}
