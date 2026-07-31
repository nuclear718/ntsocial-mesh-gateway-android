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
package com.ntsocial.meshlink.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleScanResultGateTest {

    @Test
    fun firstResultWins() {
        val gate = SingleScanResultGate()
        val results = mutableListOf<String?>()

        assertTrue(gate.tryDeliver("first", results::add))
        assertFalse(gate.tryDeliver("second", results::add))

        assertEquals(listOf("first"), results)
    }

    @Test
    fun dismissalBlocksLateCameraResult() {
        val gate = SingleScanResultGate()
        val results = mutableListOf<String?>()

        assertTrue(gate.tryDeliver(null, results::add))
        assertFalse(gate.tryDeliver("late", results::add))

        assertEquals(listOf(null), results)
    }
}
