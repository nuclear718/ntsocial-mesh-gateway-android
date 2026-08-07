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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.Telemetry

/** Interface for managing the connection lifecycle and status with the mesh radio. */
interface MeshConnectionManager {
    /** Whether the selected node explicitly opts into live phone location and is not configured as fixed-position. */
    val locationSharingRequested: StateFlow<Boolean>

    /**
     * Whether all shared preconditions for feeding phone location are satisfied: explicit per-node opt-in, a connected
     * selected node, and no fixed-position configuration.
     */
    val shouldProvideLocation: StateFlow<Boolean>

    /** Re-evaluates the latest phone-location state without changing the user's preference. */
    fun reconcileLocation()

    /** Called when the radio configuration has been fully loaded. */
    fun onRadioConfigLoaded()

    /** Initiates the configuration synchronization stage. */
    fun startConfigOnly()

    /**
     * Requests a one-shot config readback only from the exact configured session. This reliability path deliberately
     * does not arm the ordinary handshake stall/reconnect guard.
     */
    fun startConfigOnlyForSession(expectedRadioSessionEpoch: Long): Boolean = false

    /** Initiates the node information synchronization stage. */
    fun startNodeInfoOnly()

    /**
     * Called when the node database for [radioSessionEpoch] is ready and fully populated.
     *
     * Returns false when that captured radio session is stale; callers must not publish Connected or perform any
     * remaining completion side effect in that case.
     */
    suspend fun onNodeDbReady(radioSessionEpoch: Long): Boolean

    /** Updates the telemetry information for the local node. */
    fun updateTelemetry(t: Telemetry)

    /** Updates the current status notification. */
    fun updateStatusNotification(telemetry: Telemetry? = null)
}
