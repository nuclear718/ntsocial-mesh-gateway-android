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
import android.os.Build

/** Pure result used to keep the Android location listener aligned with the service's effective type. */
internal data class LocationForegroundDecision(val serviceType: Int, val locationAccessAllowed: Boolean)

/**
 * Chooses the foreground-service type without claiming location for a user who did not explicitly opt in.
 *
 * Android 11+ prevents a foreground service started from the background from using while-in-use location unless the app
 * has ACCESS_BACKGROUND_LOCATION (which this app intentionally does not request). Android 14+ additionally rejects the
 * invalid type transition at startForeground time instead of waiting for the resource access.
 */
internal fun locationForegroundDecision(
    locationRequested: Boolean,
    hasLocationPermission: Boolean,
    systemLocationEnabled: Boolean,
    appInForeground: Boolean,
    currentServiceType: Int = 0,
    sdkInt: Int = Build.VERSION.SDK_INT,
): LocationForegroundDecision {
    val currentlyHasLocation =
        sdkInt >= Build.VERSION_CODES.Q && currentServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0
    val canAcquireLocationType = sdkInt < Build.VERSION_CODES.R || appInForeground || currentlyHasLocation
    val allowLocation = locationRequested && hasLocationPermission && systemLocationEnabled && canAcquireLocationType

    val serviceType =
        if (sdkInt < Build.VERSION_CODES.Q) {
            0
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                if (allowLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        }
    return LocationForegroundDecision(serviceType = serviceType, locationAccessAllowed = allowLocation)
}
