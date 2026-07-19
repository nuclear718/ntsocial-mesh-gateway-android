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

import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.model.InterfaceId

/**
 * Creates [RadioTransport] instances for specific device addresses.
 *
 * Implemented per-platform to provide the correct hardware transport (BLE, Serial, TCP).
 */
interface RadioTransportFactory {
    /** The device types supported by this factory. */
    val supportedDeviceTypes: List<DeviceType>

    /** Whether the current environment forces use of a mock transport, such as an automated device lab. */
    fun isMockTransport(): Boolean

    /** Creates a transport for the given [address], or a NOP implementation if invalid/unsupported. */
    fun createTransport(address: String, service: RadioInterfaceService): RadioTransport

    /** Checks if the given [address] represents a valid, supported transport type. */
    fun isAddressValid(address: String?): Boolean

    /** Constructs a full radio address for the specific [interfaceId] and [rest] identifier. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String
}
