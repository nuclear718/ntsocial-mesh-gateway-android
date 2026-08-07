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
package com.ntsocial.meshlink.ios.runtime

import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.flow.StateFlow

/** BLE radio discovered by the iOS connection UI. */
internal data class RadioUiDevice(val name: String?, val peripheralId: String, val rssi: Int?)

/** Platform radio facts exposed to the shared iOS shell. */
@Suppress("LongParameterList")
internal data class RadioUiState(
    val available: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val scanning: Boolean = false,
    val devices: List<RadioUiDevice> = emptyList(),
    val selectedPeripheralId: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val connectionProgress: String? = null,
    val errorMessage: String? = null,
)

/** Narrow radio owner used by the BLE-only iOS shell. */
internal interface RadioUiPort {
    val state: StateFlow<RadioUiState>

    fun refreshBluetooth()

    fun startScan()

    fun stopScan()

    fun connect(peripheralId: String)

    fun disconnect()

    fun forget()

    fun close()
}

/** Creates the platform implementation without exposing Kable or host lifecycle details to common UI code. */
internal expect fun createRadioUiPort(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    serviceRepository: ServiceRepository,
    channelOperationLock: ChannelOperationLock,
): RadioUiPort
