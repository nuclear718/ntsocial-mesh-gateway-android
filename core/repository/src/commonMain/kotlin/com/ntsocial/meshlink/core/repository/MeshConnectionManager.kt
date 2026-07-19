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

import org.meshtastic.proto.Telemetry

/** Interface for managing the connection lifecycle and status with the mesh radio. */
interface MeshConnectionManager {
    /** Called when the radio configuration has been fully loaded. */
    fun onRadioConfigLoaded()

    /** Initiates the configuration synchronization stage. */
    fun startConfigOnly()

    /** Initiates the node information synchronization stage. */
    fun startNodeInfoOnly()

    /** Called when the node database is ready and fully populated. */
    fun onNodeDbReady()

    /** Updates the telemetry information for the local node. */
    fun updateTelemetry(t: Telemetry)

    /** Updates the current status notification. */
    fun updateStatusNotification(telemetry: Telemetry? = null)
}
