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

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayNativeText
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import okio.ByteString

object AppleGatewayCommandValidator {
    private val CLIENT_MESSAGE_ID = Regex("^[0-9A-Fa-f]{${AppleGatewayContract.CLIENT_MESSAGE_ID_HEX_LENGTH}}$")
    private val SOURCE_CHANNEL_ID = Regex("^meshtastic:[0-9a-f]{64}$")
    private val ROUTE_TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
    private val NODE_ID = Regex("^![0-9a-fA-F]{8}$")

    @Suppress("ReturnCount")
    fun validate(
        command: AppleGatewayCommand,
        route: AppleGatewayRoute,
        authenticationKey: ByteString,
        nowMillis: Long,
    ): AppleGatewayValidationResult {
        val structuralFailure =
            preAuthenticationFailure(
                command = command,
                expectedCallerId = command.callerId,
                activeKeyVersion = command.keyVersion,
                nowMillis = nowMillis,
            )
        if (structuralFailure != null) return AppleGatewayValidationResult.Invalid(structuralFailure)
        if (!AppleGatewayAuthenticator.verify(command, authenticationKey)) {
            return AppleGatewayValidationResult.Invalid(AppleGatewayRejectionReason.INVALID_AUTHENTICATION)
        }
        if (!route.matches(command, nowMillis)) {
            return AppleGatewayValidationResult.Invalid(AppleGatewayRejectionReason.INVALID_ROUTE)
        }
        val bodyFailure = bodyFailure(command.body)
        if (bodyFailure != null) return AppleGatewayValidationResult.Invalid(bodyFailure)
        return AppleGatewayValidationResult.Valid(
            canonicalClientMessageId = command.clientMessageId.uppercase(),
            requestFingerprint = AppleGatewayCommandCodec.requestFingerprint(command),
        )
    }

    /** Static validation performed before selecting or exercising authentication material. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun preAuthenticationFailure(
        command: AppleGatewayCommand,
        expectedCallerId: String,
        activeKeyVersion: Int,
        nowMillis: Long,
    ): AppleGatewayRejectionReason? {
        if (command.schemaVersion != AppleGatewayContract.SCHEMA_VERSION) {
            return AppleGatewayRejectionReason.UNSUPPORTED_SCHEMA
        }
        if (
            command.callerId != expectedCallerId ||
            command.callerId.isBlank() ||
            command.callerId.length > AppleGatewayContract.MAX_CALLER_ID_LENGTH
        ) {
            return AppleGatewayRejectionReason.INVALID_CALLER
        }
        if (command.requestId.isBlank() || command.requestId.length > AppleGatewayContract.MAX_REQUEST_ID_LENGTH) {
            return AppleGatewayRejectionReason.INVALID_REQUEST_ID
        }
        if (!CLIENT_MESSAGE_ID.matches(command.clientMessageId)) {
            return AppleGatewayRejectionReason.INVALID_CLIENT_MESSAGE_ID
        }
        if (
            command.sourceChannelId.length > AppleGatewayContract.MAX_SOURCE_CHANNEL_ID_LENGTH ||
            !SOURCE_CHANNEL_ID.matches(command.sourceChannelId)
        ) {
            return AppleGatewayRejectionReason.INVALID_SOURCE_CHANNEL_ID
        }
        if (!ROUTE_TOKEN.matches(command.routeToken)) {
            return AppleGatewayRejectionReason.INVALID_ROUTE_TOKEN
        }
        if (
            command.radioGeneration.isBlank() ||
            command.radioGeneration.length > AppleGatewayContract.MAX_GENERATION_LENGTH
        ) {
            return AppleGatewayRejectionReason.INVALID_GENERATION
        }
        val commandLifetime = command.expiresAtMillis - command.issuedAtMillis
        val latestAcceptedIssueTime =
            if (nowMillis > Long.MAX_VALUE - AppleGatewayContract.COMMAND_CLOCK_SKEW_MILLIS) {
                Long.MAX_VALUE
            } else {
                nowMillis + AppleGatewayContract.COMMAND_CLOCK_SKEW_MILLIS
            }
        if (
            command.expiresAtMillis <= command.issuedAtMillis ||
            commandLifetime <= 0 ||
            commandLifetime > AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS ||
            command.issuedAtMillis > latestAcceptedIssueTime
        ) {
            return AppleGatewayRejectionReason.INVALID_TIME_WINDOW
        }
        if (command.expiresAtMillis <= nowMillis) return AppleGatewayRejectionReason.EXPIRED
        if (command.keyVersion <= 0 || command.keyVersion != activeKeyVersion) {
            return AppleGatewayRejectionReason.INVALID_KEY_VERSION
        }
        if (command.nonce.size != AppleGatewayContract.COMMAND_NONCE_SIZE_BYTES) {
            return AppleGatewayRejectionReason.INVALID_NONCE
        }
        return null
    }

    fun bodyFailure(body: AppleGatewayCommandBody): AppleGatewayRejectionReason? = when (body) {
        is AppleGatewayCommandBody.NtsocialEnvelope ->
            when {
                body.rawEnvelope.size !in 1..NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES ||
                    NtsocialEnvelopeCodec.decode(body.rawEnvelope) == null ->
                    AppleGatewayRejectionReason.INVALID_ENVELOPE

                body.destination != null &&
                    body.destination != DataPacket.ID_BROADCAST &&
                    !NODE_ID.matches(body.destination) -> AppleGatewayRejectionReason.INVALID_DESTINATION

                body.hopLimit < 0 -> AppleGatewayRejectionReason.INVALID_HOP_LIMIT

                else -> null
            }

        is AppleGatewayCommandBody.NativeBroadcastText ->
            if (NtsocialGatewayNativeText.isValid(body.text)) {
                null
            } else {
                AppleGatewayRejectionReason.INVALID_NATIVE_TEXT
            }
    }

    private fun AppleGatewayRoute.matches(command: AppleGatewayCommand, nowMillis: Long): Boolean =
        expiresAtMillis > nowMillis &&
            callerId == command.callerId &&
            sourceChannelId == command.sourceChannelId &&
            token == command.routeToken &&
            radioGeneration == command.radioGeneration &&
            capturedSlotIndex >= 0 &&
            command.body.requiredRouteCapability() in capabilities
}

object AppleGatewayOverlayIngressPolicy {
    fun accepts(portNumber: Int, rawEnvelope: ByteString): Boolean = NtsocialTransport.isInboundPort(portNumber) &&
        rawEnvelope.size <= NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES &&
        NtsocialEnvelopeCodec.decode(rawEnvelope) != null
}
