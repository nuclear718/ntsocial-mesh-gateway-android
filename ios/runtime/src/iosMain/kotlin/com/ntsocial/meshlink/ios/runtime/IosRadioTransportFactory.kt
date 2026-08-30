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

import com.ntsocial.meshlink.core.ble.BleConnectionFactory
import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.network.radio.BaseRadioTransportFactory
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioTransport

/** Every iOS endpoint receives its own Kable-backed BLE transport; TCP/USB/Serial remain unavailable. */
internal class IosRadioTransportFactory(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE)

    override fun isMockTransport(): Boolean = false

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
        error("Unsupported iOS radio transport: $address")
}
