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

import android.content.pm.ServiceInfo
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationForegroundPolicyTest {
    private val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    private val connectedAndLocation = connected or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @Test
    fun `location type requires explicit opt-in permission and enabled system location`() {
        val optedOut = decide(locationRequested = false)
        val denied = decide(hasLocationPermission = false)
        val systemDisabled = decide(systemLocationEnabled = false)
        val allowed = decide()

        assertEquals(connected, optedOut.serviceType)
        assertEquals(connected, denied.serviceType)
        assertEquals(connected, systemDisabled.serviceType)
        assertFalse(optedOut.locationAccessAllowed)
        assertFalse(denied.locationAccessAllowed)
        assertFalse(systemDisabled.locationAccessAllowed)
        assertEquals(connectedAndLocation, allowed.serviceType)
        assertTrue(allowed.locationAccessAllowed)
    }

    @Test
    fun `Android 14 background start cannot add location type`() {
        val decision = decide(appInForeground = false, sdkInt = 34)

        assertEquals(connected, decision.serviceType)
        assertFalse(decision.locationAccessAllowed)
    }

    @Test
    fun `Android 11 through 13 background start cannot use while-in-use location`() {
        listOf(30, 33).forEach { sdkInt ->
            val decision = decide(appInForeground = false, sdkInt = sdkInt)

            assertEquals(connected, decision.serviceType, "sdk=$sdkInt")
            assertFalse(decision.locationAccessAllowed, "sdk=$sdkInt")
        }
    }

    @Test
    fun `Android 10 background service can use its typed foreground location permission`() {
        val decision = decide(appInForeground = false, sdkInt = 29)

        assertEquals(connectedAndLocation, decision.serviceType)
        assertTrue(decision.locationAccessAllowed)
    }

    @Test
    fun `Android 14 foreground reconcile promotes a connected-only service for an existing opt-in`() {
        val decision = decide(appInForeground = true, currentServiceType = connected, sdkInt = 34)

        assertEquals(connectedAndLocation, decision.serviceType)
        assertTrue(decision.locationAccessAllowed)
    }

    @Test
    fun `Android 14 preserves a location type acquired while foreground`() {
        val decision = decide(appInForeground = false, currentServiceType = connectedAndLocation, sdkInt = 34)

        assertEquals(connectedAndLocation, decision.serviceType)
        assertTrue(decision.locationAccessAllowed)
    }

    @Test
    fun `opt-out drops a previously acquired location type`() {
        val decision =
            decide(
                locationRequested = false,
                appInForeground = false,
                currentServiceType = connectedAndLocation,
                sdkInt = 34,
            )

        assertEquals(connected, decision.serviceType)
        assertFalse(decision.locationAccessAllowed)
    }

    @Test
    fun `pre Android 10 permits access without a typed foreground service`() {
        val decision = decide(appInForeground = false, sdkInt = 28)

        assertEquals(0, decision.serviceType)
        assertTrue(decision.locationAccessAllowed)
    }

    private fun decide(
        locationRequested: Boolean = true,
        hasLocationPermission: Boolean = true,
        systemLocationEnabled: Boolean = true,
        appInForeground: Boolean = true,
        currentServiceType: Int = 0,
        sdkInt: Int = 37,
    ) = locationForegroundDecision(
        locationRequested = locationRequested,
        hasLocationPermission = hasLocationPermission,
        systemLocationEnabled = systemLocationEnabled,
        appInForeground = appInForeground,
        currentServiceType = currentServiceType,
        sdkInt = sdkInt,
    )
}
