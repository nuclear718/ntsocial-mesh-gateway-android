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
package com.ntsocial.meshlink.core.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WifiCredentialsTest {

    @Test
    fun extractWifiCredentials_shouldParseValidQrCode() {
        val qrCode = "WIFI:S:MyNetwork;P:MyPassword;;"
        val (ssid, password) = extractWifiCredentials(qrCode)
        assertEquals("MyNetwork", ssid)
        assertEquals("MyPassword", password)
    }

    @Test
    fun extractWifiCredentials_shouldReturnNullForInvalidQrCode() {
        val qrCode = "INVALID_QR_CODE"
        val (ssid, password) = extractWifiCredentials(qrCode)
        assertNull(ssid)
        assertNull(password)
    }

    @Test
    fun extractWifiCredentials_shouldHandleMissingPassword() {
        val qrCode = "WIFI:S:MyNetwork;;"
        val (ssid, password) = extractWifiCredentials(qrCode)
        assertNull(ssid)
        assertNull(password)
    }
}
