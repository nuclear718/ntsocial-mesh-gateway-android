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

/** The smallest radio-side channel snapshot needed to issue caller/source/slot-bound routes. */
data class AppleGatewayRadioChannelIdentity(
    val sourceChannelId: String,
    val slotIndex: Int,
    val displayName: String,
    val role: String,
    val securityClass: String,
    val capabilities: Set<AppleGatewayRouteCapability>,
)

data class AppleGatewayRadioSnapshot(
    val readiness: AppleGatewayReadiness,
    val channels: List<AppleGatewayRadioChannelIdentity>,
    val historyEpoch: String?,
    val overlayHighWater: Long,
    val nativeTextHighWater: Long,
    /** Process-private equality key for the complete routing configuration; never persisted or exported. */
    val routingContext: ByteString = ByteString.EMPTY,
)

data class AppleGatewayOverlayAdmission(
    val packetId: Int,
    val canonicalClientMessageId: String,
    val sourceChannelId: String,
    val capturedSlotIndex: Int,
    val rawEnvelope: ByteString,
    val destination: String?,
    val hopLimit: Int,
    val wantAck: Boolean,
)

data class AppleGatewayNativeTextAdmission(
    val packetId: Int,
    val canonicalClientMessageId: String,
    val sourceChannelId: String,
    val capturedSlotIndex: Int,
    val text: String,
)

/**
 * Result of local durable admission only. It must never imply firmware airtime or remote receipt.
 *
 * Implementations must make admission idempotent for an identical deterministic packet ID and exact content. This is
 * what closes the crash window between durable radio admission and the private ledger's ACCEPTED commit.
 */
sealed interface AppleGatewayRadioAdmissionResult {
    data object Accepted : AppleGatewayRadioAdmissionResult

    data class TransientFailure(val reason: AppleGatewayRejectionReason) : AppleGatewayRadioAdmissionResult

    data class PermanentFailure(val reason: AppleGatewayRejectionReason) : AppleGatewayRadioAdmissionResult
}

/** Host adapter over the existing KMP radio repositories; it intentionally exposes no transport implementation. */
interface AppleGatewayRadioPort {
    suspend fun snapshot(): AppleGatewayRadioSnapshot

    suspend fun durablyAdmitOverlay(admission: AppleGatewayOverlayAdmission): AppleGatewayRadioAdmissionResult

    suspend fun durablyAdmitNativeText(admission: AppleGatewayNativeTextAdmission): AppleGatewayRadioAdmissionResult
}

fun interface AppleGatewayWakeSink {
    /** Payload-free hint only; the receiver must re-read the App Group store. */
    fun stateChanged()
}

object NoopAppleGatewayWakeSink : AppleGatewayWakeSink {
    override fun stateChanged() = Unit
}
