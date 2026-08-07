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

import okio.ByteString

enum class AppleGatewayReadiness {
    NOT_READY,
    BLUETOOTH_UNAVAILABLE,
    DISCONNECTED,
    CONNECTING,
    CONFIGURING,
    READY,
}

data class AppleGatewayStatus(
    val schemaVersion: Int = AppleGatewayContract.SCHEMA_VERSION,
    val providerInstanceId: String,
    val readiness: AppleGatewayReadiness,
    val radioGeneration: String,
    val historyEpoch: String?,
    val overlayHighWater: Long,
    val nativeTextHighWater: Long,
    val activeKeyVersion: Int,
    val updatedAtMillis: Long,
)

data class AppleGatewayChannelProjection(
    val sourceChannelId: String,
    val slotIndex: Int,
    val displayName: String,
    val role: String,
    val securityClass: String,
    val capabilities: Set<AppleGatewayRouteCapability>,
    val routeToken: String,
    val routeExpiresAtMillis: Long,
    val radioGeneration: String,
)

data class AppleGatewayCallerProjection(
    val callerId: String,
    val activeKeyVersion: Int,
    val revoked: Boolean,
    val lastSeenAtMillis: Long,
)

enum class AppleGatewayRouteCapability {
    SEND_NTSOCIAL_ENVELOPE,
    SEND_NATIVE_BROADCAST_TEXT,
}

data class AppleGatewayRoute(
    val callerId: String,
    val sourceChannelId: String,
    val capturedSlotIndex: Int,
    val capabilities: Set<AppleGatewayRouteCapability>,
    val token: String,
    val radioGeneration: String,
    val expiresAtMillis: Long,
)

sealed interface AppleGatewayCommandBody {
    data class NtsocialEnvelope(
        val rawEnvelope: ByteString,
        val destination: String? = null,
        val hopLimit: Int = 0,
        val wantAck: Boolean = true,
    ) : AppleGatewayCommandBody

    /** Native Meshtastic text is deliberately broadcast-only. */
    data class NativeBroadcastText(val text: String) : AppleGatewayCommandBody
}

internal fun AppleGatewayCommandBody.requiredRouteCapability(): AppleGatewayRouteCapability = when (this) {
    is AppleGatewayCommandBody.NtsocialEnvelope -> AppleGatewayRouteCapability.SEND_NTSOCIAL_ENVELOPE
    is AppleGatewayCommandBody.NativeBroadcastText -> AppleGatewayRouteCapability.SEND_NATIVE_BROADCAST_TEXT
}

data class AppleGatewayCommand(
    val schemaVersion: Int = AppleGatewayContract.SCHEMA_VERSION,
    val callerId: String,
    val requestId: String,
    val clientMessageId: String,
    val sourceChannelId: String,
    val routeToken: String,
    val radioGeneration: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val keyVersion: Int,
    val nonce: ByteString,
    val body: AppleGatewayCommandBody,
    val authenticationTag: ByteString = ByteString.EMPTY,
)

enum class AppleGatewayCommandResultState {
    PENDING_PROVIDER_WAKE,
    PROCESSING,
    ACCEPTED_LOCAL,
    REJECTED,
    QUEUED_RADIO,
}

data class AppleGatewayCommandResult(
    val callerId: String,
    val clientMessageId: String,
    val resultSequence: Long,
    val state: AppleGatewayCommandResultState,
    val packetId: Int?,
    val reason: AppleGatewayRejectionReason?,
    val updatedAtMillis: Long,
)

data class AppleGatewayOverlayIngress(
    val historyEpoch: String,
    val changeSequence: Long,
    val sourceChannelId: String,
    val sourceMessageId: String,
    val sourceNodeId: String,
    val packetId: UInt,
    val portNumber: Int,
    val rawEnvelope: ByteString,
    val receivedAtMillis: Long,
)

data class AppleGatewayOverlayIngressPayload(
    val sourceChannelId: String,
    val sourceMessageId: String,
    val sourceNodeId: String,
    val packetId: UInt,
    val portNumber: Int,
    val rawEnvelope: ByteString,
    val receivedAtMillis: Long,
)

/** Runtime supplies only broadcast text whose stable IDs were captured at private-Room insertion time. */
data class AppleGatewayNativeMessageChange(
    val historyEpoch: String,
    val changeSequence: Long,
    val sourceChannelId: String,
    val sourceMessageId: String,
    val fromNodeId: String,
    val packetId: UInt,
    val text: String,
    val receivedAtMillis: Long,
    val originClientMessageId: String?,
)

enum class AppleGatewayRejectionReason(val wireValue: String) {
    UNSUPPORTED_SCHEMA("unsupported_schema"),
    INVALID_CALLER("invalid_caller"),
    INVALID_REQUEST_ID("invalid_request_id"),
    INVALID_CLIENT_MESSAGE_ID("invalid_client_message_id"),
    INVALID_SOURCE_CHANNEL_ID("invalid_source_channel_id"),
    INVALID_ROUTE_TOKEN("invalid_route_token"),
    INVALID_GENERATION("invalid_generation"),
    INVALID_TIME_WINDOW("invalid_time_window"),
    EXPIRED("expired"),
    INVALID_KEY_VERSION("invalid_key_version"),
    INVALID_NONCE("invalid_nonce"),
    INVALID_ENVELOPE("invalid_envelope"),
    INVALID_DESTINATION("invalid_destination"),
    INVALID_HOP_LIMIT("invalid_hop_limit"),
    INVALID_NATIVE_TEXT("invalid_native_text"),
    INVALID_AUTHENTICATION("invalid_authentication"),
    INVALID_ROUTE("invalid_route"),
    NONCE_REPLAY("nonce_replay"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    RADIO_NOT_READY("radio_not_ready"),
    RADIO_REJECTED("radio_rejected"),
    QUEUE_FAILED("queue_failed"),
}

enum class AppleGatewayNonceReservation {
    RESERVED,
    SAME_COMMAND,
    REPLAY,
}

sealed interface AppleGatewayValidationResult {
    data class Valid(val canonicalClientMessageId: String, val requestFingerprint: String) :
        AppleGatewayValidationResult

    data class Invalid(val reason: AppleGatewayRejectionReason) : AppleGatewayValidationResult
}
