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
package com.ntsocial.meshlink.ios.runtime

import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayCommandResult
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayCommandResultState
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayProcessOutcome
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRejectionReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayCommandDrainTest {
    @Test
    fun `budget exhaustion schedules a continuation that can observe the empty mailbox`() = runTest {
        val outcomes = ArrayDeque<AppleGatewayProcessOutcome>()
        repeat(2) { outcomes += accepted }
        outcomes += AppleGatewayProcessOutcome.NoCommand

        val firstPass = drainGatewayCommands(maxCommands = 2) { outcomes.removeAt(0) }
        val continuation = drainGatewayCommands(maxCommands = 2) { outcomes.removeAt(0) }

        assertTrue(firstPass)
        assertFalse(continuation)
        assertEquals(0, outcomes.size)
    }

    @Test
    fun `empty mailbox does not schedule a continuation`() = runTest {
        assertFalse(drainGatewayCommands(maxCommands = 4) { AppleGatewayProcessOutcome.NoCommand })
    }

    @Test
    fun `retryable rejection schedules a continuation before the page budget is exhausted`() = runTest {
        var calls = 0
        val shouldContinue =
            drainGatewayCommands(maxCommands = 4) {
                calls += 1
                retryableRejection
            }

        assertTrue(shouldContinue)
        assertEquals(1, calls)
    }

    private companion object {
        val accepted =
            AppleGatewayProcessOutcome.Accepted(
                result =
                AppleGatewayCommandResult(
                    callerId = "com.ntsocial.ios",
                    clientMessageId = "0".repeat(32),
                    resultSequence = 1,
                    state = AppleGatewayCommandResultState.ACCEPTED_LOCAL,
                    packetId = 1,
                    reason = null,
                    updatedAtMillis = 1,
                ),
                replayed = false,
            )

        val retryableRejection =
            AppleGatewayProcessOutcome.Rejected(
                result =
                AppleGatewayCommandResult(
                    callerId = "com.ntsocial.ios",
                    clientMessageId = "1".repeat(32),
                    resultSequence = 2,
                    state = AppleGatewayCommandResultState.PENDING_PROVIDER_WAKE,
                    packetId = null,
                    reason = AppleGatewayRejectionReason.QUEUE_FAILED,
                    updatedAtMillis = 2,
                ),
                retryable = true,
            )
    }
}
