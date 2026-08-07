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

import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayProcessOutcome

/**
 * Processes one bounded mailbox page and reports whether a delayed continuation is needed.
 *
 * Exhausting [maxCommands] is deliberately treated as pending work. The delayed continuation will cheaply discover an
 * empty mailbox on its next pass when the page happened to contain exactly the remaining command count.
 */
internal suspend fun drainGatewayCommands(
    maxCommands: Int,
    processNext: suspend () -> AppleGatewayProcessOutcome,
): Boolean {
    require(maxCommands > 0)
    var remainingBudget = maxCommands
    var state = GatewayDrainState.ACTIVE
    while (remainingBudget > 0 && state == GatewayDrainState.ACTIVE) {
        when (val outcome = processNext()) {
            AppleGatewayProcessOutcome.NoCommand -> state = GatewayDrainState.EMPTY

            is AppleGatewayProcessOutcome.Accepted -> remainingBudget -= 1

            is AppleGatewayProcessOutcome.Rejected -> {
                if (outcome.retryable) {
                    state = GatewayDrainState.RETRY_LATER
                } else {
                    remainingBudget -= 1
                }
            }
        }
    }
    return state != GatewayDrainState.EMPTY
}

private enum class GatewayDrainState {
    ACTIVE,
    EMPTY,
    RETRY_LATER,
}
