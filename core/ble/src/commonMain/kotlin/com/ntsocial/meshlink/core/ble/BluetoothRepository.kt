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
package com.ntsocial.meshlink.core.ble

import kotlinx.coroutines.flow.StateFlow

/** Repository responsible for Bluetooth availability and bonding. */
interface BluetoothRepository {
    /** The current state of Bluetooth on the device. */
    val state: StateFlow<BluetoothState>

    /** Refreshes the Bluetooth state. */
    fun refreshState()

    /** Returns true if the given address is valid. */
    fun isValid(bleAddress: String): Boolean

    /** Returns true if the given address is bonded. */
    fun isBonded(address: String): Boolean

    /**
     * Resolves a previously known device without requiring a current advertisement.
     *
     * The default implementation uses the platform's bonded-device snapshot. Apple platforms override this because
     * CoreBluetooth has no public bond list: the override reconstructs the saved identifier and completes protected
     * GATT verification before returning, so the same prepared session can be handed to the transport without a scan.
     */
    suspend fun prepareKnownDevice(address: String): BleDevice? =
        state.value.bondedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) }

    /** Releases a platform-prepared connection that was not claimed by a [BleConnection]. */
    suspend fun discardPreparedDevice(device: BleDevice) = Unit

    /** Initiates bonding with the given device. */
    suspend fun bond(device: BleDevice)
}

/** Represents the state of Bluetooth on the device. */
data class BluetoothState(
    /** True if the application has the required Bluetooth permissions. */
    val hasPermissions: Boolean = false,

    /** True if Bluetooth is enabled on the device. */
    val enabled: Boolean = false,

    /** A list of bonded devices. */
    val bondedDevices: List<BleDevice> = emptyList(),
)
