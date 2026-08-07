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
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal actual fun createRadioUiPort(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    serviceRepository: ServiceRepository,
    channelOperationLock: ChannelOperationLock,
): RadioUiPort = UnavailableRadioUiPort

/** This iOS-host UI bridge has no runtime meaning on Android or Desktop targets. */
private object UnavailableRadioUiPort : RadioUiPort {
    private val mutableState = MutableStateFlow(RadioUiState())
    override val state: StateFlow<RadioUiState> = mutableState.asStateFlow()

    override fun refreshBluetooth() = Unit

    override fun startScan() = Unit

    override fun stopScan() = Unit

    override fun connect(peripheralId: String) = Unit

    override fun disconnect() = Unit

    override fun forget() = Unit

    override fun close() = Unit
}
