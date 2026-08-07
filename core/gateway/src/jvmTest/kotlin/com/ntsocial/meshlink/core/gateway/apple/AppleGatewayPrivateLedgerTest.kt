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

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleGatewayPrivateLedgerTest {
    private val directory = Files.createTempDirectory("meshlink-private-ledger-")
    private val path = directory.resolve("private.sqlite").toString()

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `pending and accepted replay survive store reconstruction while conflict fails`() = runTest {
        val ledger = AppleGatewayPrivateLedger(path)
        val pending = assertIs<AppleGatewayLedgerReservation.Pending>(ledger.reserve(CALLER, CLIENT_ID, FINGERPRINT))
        assertEquals(pending, AppleGatewayPrivateLedger(path).reserve(CALLER, CLIENT_ID, FINGERPRINT))
        ledger.markAccepted(CALLER, CLIENT_ID, FINGERPRINT, pending.packetId)
        assertEquals(
            AppleGatewayLedgerReservation.Accepted(pending.packetId),
            AppleGatewayPrivateLedger(path).reserve(CALLER, CLIENT_ID, FINGERPRINT),
        )
        assertEquals(AppleGatewayLedgerState.ACCEPTED, AppleGatewayPrivateLedger(path).lookup(CALLER, CLIENT_ID)?.state)
        assertEquals(AppleGatewayLedgerReservation.Conflict, ledger.reserve(CALLER, CLIENT_ID, "B".repeat(64)))
    }

    @Test
    fun `capacity is scoped per caller and insertion ordered`() = runTest {
        val ledger = AppleGatewayPrivateLedger(path)
        repeat(AppleGatewayContract.MAX_LEDGER_RECORDS_PER_CALLER + 2) { index ->
            ledger.reserve(CALLER, index.toString(16).padStart(32, '0').uppercase(), FINGERPRINT)
        }
        assertEquals(null, ledger.lookup(CALLER, "0".repeat(32)))
        assertEquals(null, ledger.lookup(CALLER, "0".repeat(31) + "1"))
        assertEquals(AppleGatewayLedgerState.PENDING, ledger.lookup(CALLER, "0".repeat(30) + "02")?.state)

        val other = "com.ntsocial.other"
        ledger.reserve(other, CLIENT_ID, FINGERPRINT)
        assertEquals(CLIENT_ID, ledger.lookup(other, CLIENT_ID)?.clientMessageId)
    }

    private companion object {
        const val CALLER = AppleGatewayContract.PARENT_CALLER_ID
        const val CLIENT_ID = "00112233445566778899AABBCCDDEEFF"
        const val FINGERPRINT = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
