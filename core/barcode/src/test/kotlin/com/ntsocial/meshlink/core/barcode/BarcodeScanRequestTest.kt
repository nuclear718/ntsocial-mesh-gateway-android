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
import org.junit.Test

class BarcodeScanRequestTest {
    @Test
    fun delayedPermissionOrCameraResultKeepsStartingOwner() {
        val results = mutableListOf<String>()
        var callback: (String?) -> Unit = { results += "A:$it" }
        val first = BarcodeScanRequest(callback)
        callback = { results += "B:$it" }
        val second = BarcodeScanRequest(callback)
        first.deliver("channel-A")
        first.deliver("duplicate")
        second.deliver("channel-B")
        assertEquals(listOf("A:channel-A", "B:channel-B"), results)
    }

    @Test
    fun disposedRequestCannotDeliverLateResults() {
        val results = mutableListOf<String?>()
        val request = BarcodeScanRequest { results += it }
        request.cancel()
        request.deliver("late-channel")
        assertEquals(emptyList<String?>(), results)
    }
}
