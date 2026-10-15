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
package com.ntsocial.meshlink.feature.connections.ui

import com.ntsocial.meshlink.core.navigation.EndpointTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionDeviceIdentityTest {
    private val primary = EndpointTarget("primary", "primary-runtime")
    private val secondary = EndpointTarget("secondary", "secondary-runtime")

    @Test
    fun `unowned fleet page cannot display a remembered primary device`() {
        assertEquals(ConnectionDeviceIdentity(), identity(owner = null, candidate = primary))
    }

    @Test
    fun `secondary page displays only its own device identity`() {
        assertEquals(ConnectionDeviceIdentity(), identity(owner = secondary, candidate = primary))
        assertEquals(ConnectionDeviceIdentity("xSECONDARY", "Secondary radio"), identity(secondary, secondary))
    }

    @Test
    fun `released lease cannot remain in the connected device card`() {
        assertEquals(ConnectionDeviceIdentity(), identity(secondary, secondary, enabled = false))
        assertEquals(ConnectionDeviceIdentity(), identity(secondary, candidate = null))
    }

    @Test
    fun `retired page cannot display a replacement runtime for the same endpoint`() {
        val replacement = secondary.copy(runtimeId = "replacement-runtime")
        assertEquals(ConnectionDeviceIdentity(), identity(secondary, replacement))
    }

    @Test
    fun `remembered profile without a runtime is not an active identity`() {
        val remembered = secondary.copy(runtimeId = "")
        assertEquals(ConnectionDeviceIdentity(), identity(remembered, remembered))
    }

    private fun identity(
        owner: EndpointTarget?,
        candidate: EndpointTarget?,
        enabled: Boolean = true,
    ): ConnectionDeviceIdentity = ownedConnectionIdentity(
        owner = owner,
        candidateOwner = candidate,
        enabled = enabled,
        address = "xSECONDARY",
        name = "Secondary radio",
    )
}
