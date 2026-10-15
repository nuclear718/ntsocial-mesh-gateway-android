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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.navigation.EndpointTarget
import com.ntsocial.meshlink.feature.connections.NO_DEVICE_SELECTED

/** Host control surface for the shared, bounded four-radio fleet. */
@Composable expect fun RadioFleetPanel(modifier: Modifier = Modifier)

internal data class ConnectionDeviceIdentity(
    val address: String = NO_DEVICE_SELECTED,
    val name: String? = null,
    val onDisconnect: (() -> Unit)? = null,
)

/** Null opts single-radio hosts into their existing scanner identity; an empty identity means no active fleet owner. */
@Composable internal expect fun fleetConnectionIdentity(): ConnectionDeviceIdentity?

internal fun ownedConnectionIdentity(
    owner: EndpointTarget?,
    candidateOwner: EndpointTarget?,
    enabled: Boolean,
    address: String?,
    name: String?,
): ConnectionDeviceIdentity {
    if (owner == null || owner.runtimeId.isBlank() || owner != candidateOwner) return ConnectionDeviceIdentity()
    return if (enabled && !address.isNullOrBlank()) {
        ConnectionDeviceIdentity(address, name)
    } else {
        ConnectionDeviceIdentity()
    }
}
