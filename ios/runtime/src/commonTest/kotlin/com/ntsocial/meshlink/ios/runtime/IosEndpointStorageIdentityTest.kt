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

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosEndpointStorageIdentityTest {
    @Test
    fun `each restart-stable endpoint id receives a distinct path-safe namespace`() {
        val first = iosEndpointStorageFileStem(RadioEndpointId("123e4567-e89b-12d3-a456-426614174000"))
        val second = iosEndpointStorageFileStem(RadioEndpointId("223e4567-e89b-12d3-a456-426614174000"))

        assertEquals("123e4567e89b12d3a456426614174000", first)
        assertEquals(32, first.length)
        assertNotEquals(first, second)
    }
}
