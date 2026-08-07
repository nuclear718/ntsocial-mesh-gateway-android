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

package com.ntsocial.meshlink.core.gateway.apple

import okio.ByteString.Companion.encodeUtf8

enum class AppleGatewayLedgerState {
    PENDING,
    ACCEPTED,
}

data class AppleGatewayLedgerRecord(
    val callerId: String,
    val clientMessageId: String,
    val requestFingerprint: String,
    val state: AppleGatewayLedgerState,
    val packetId: Int,
    val insertionSequence: Long,
)

sealed interface AppleGatewayLedgerReservation {
    data class Pending(val packetId: Int) : AppleGatewayLedgerReservation

    data class Accepted(val packetId: Int) : AppleGatewayLedgerReservation

    data object Conflict : AppleGatewayLedgerReservation
}

object AppleGatewayIdempotencyPolicy {
    fun reserve(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
        existing: AppleGatewayLedgerRecord?,
    ): AppleGatewayLedgerReservation {
        if (existing == null) {
            return AppleGatewayLedgerReservation.Pending(deterministicPacketId(callerId, canonicalClientMessageId))
        }
        return when {
            existing.callerId != callerId ||
                existing.clientMessageId != canonicalClientMessageId ||
                existing.requestFingerprint != requestFingerprint -> AppleGatewayLedgerReservation.Conflict

            existing.state == AppleGatewayLedgerState.ACCEPTED ->
                AppleGatewayLedgerReservation.Accepted(existing.packetId)

            else -> AppleGatewayLedgerReservation.Pending(existing.packetId)
        }
    }

    fun trim(records: List<AppleGatewayLedgerRecord>): List<AppleGatewayLedgerRecord> = records
        .sortedBy(AppleGatewayLedgerRecord::insertionSequence)
        .takeLast(AppleGatewayContract.MAX_LEDGER_RECORDS_PER_CALLER)

    fun deterministicPacketId(callerId: String, canonicalClientMessageId: String): Int {
        val digest = "$callerId:$canonicalClientMessageId".encodeUtf8().sha256()
        val value =
            ((digest[0].toInt() and 0x7F) shl 24) or
                ((digest[1].toInt() and 0xFF) shl 16) or
                ((digest[2].toInt() and 0xFF) shl 8) or
                (digest[3].toInt() and 0xFF)
        return value.takeUnless { it == 0 } ?: 1
    }
}
