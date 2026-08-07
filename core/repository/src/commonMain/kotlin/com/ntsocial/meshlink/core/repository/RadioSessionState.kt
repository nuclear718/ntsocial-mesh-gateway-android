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
package com.ntsocial.meshlink.core.repository

import com.ntsocial.meshlink.core.model.ConnectionState

/**
 * Process-local identity of one physical radio transport/configuration session.
 *
 * [epoch] advances at every selection or transport lifecycle boundary. [configured] belongs only to that exact epoch
 * and becomes true after the Meshtastic config/node handshake completes. Consumers must not infer an active radio from
 * the selected preference alone: [isConfiguredReady] additionally requires the active transport to match it.
 */
data class RadioSessionState(
    val epoch: Long,
    val selectedDeviceAddress: String?,
    val activeDeviceAddress: String?,
    val transportConnectionState: ConnectionState,
    val configured: Boolean,
) {
    val isConfiguredReady: Boolean
        get() =
            epoch > 0 &&
                selectedDeviceAddress != null &&
                selectedDeviceAddress == activeDeviceAddress &&
                transportConnectionState == ConnectionState.Connected &&
                configured
}
