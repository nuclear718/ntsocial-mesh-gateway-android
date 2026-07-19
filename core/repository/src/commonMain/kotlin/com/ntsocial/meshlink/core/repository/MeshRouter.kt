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

/** Interface for the central router that orchestrates specialized mesh packet handlers. */
interface MeshRouter {
    /** Access to the data handler. */
    val dataHandler: MeshDataHandler

    /** Access to the configuration handler. */
    val configHandler: MeshConfigHandler

    /** Access to the traceroute handler. */
    val tracerouteHandler: TracerouteHandler

    /** Access to the neighbor info handler. */
    val neighborInfoHandler: NeighborInfoHandler

    /** Access to the configuration flow manager. */
    val configFlowManager: MeshConfigFlowManager

    /** Access to the MQTT manager. */
    val mqttManager: MqttManager

    /** Access to the action handler. */
    val actionHandler: MeshActionHandler

    /** Access to the XModem file-transfer manager. */
    val xmodemManager: XModemManager
}
