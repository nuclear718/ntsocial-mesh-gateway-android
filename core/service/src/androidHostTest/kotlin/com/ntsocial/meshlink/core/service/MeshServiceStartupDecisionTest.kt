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
package com.ntsocial.meshlink.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshServiceStartupDecisionTest {
    @Test
    fun `persisted device keeps the service running before or after grace`() {
        listOf(false, true).forEach { graceElapsed ->
            assertEquals(
                MeshServiceStartupDecision.KEEP_RUNNING,
                meshServiceStartupDecision("bAC:A7:04:05:9A:91", graceElapsed),
            )
        }
    }

    @Test
    fun `unloaded selection gets a startup grace instead of losing started-service ownership`() {
        listOf(null, "", "  ", "n", "N").forEach { address ->
            assertEquals(
                MeshServiceStartupDecision.AWAIT_DEVICE,
                meshServiceStartupDecision(address, graceElapsed = false),
            )
        }
    }

    @Test
    fun `missing selection stops only after startup grace`() {
        listOf(null, "", "  ", "n", "N").forEach { address ->
            assertEquals(MeshServiceStartupDecision.STOP, meshServiceStartupDecision(address, graceElapsed = true))
        }
    }
}
