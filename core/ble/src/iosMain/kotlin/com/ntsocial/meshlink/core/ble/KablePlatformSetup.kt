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

import co.touchlab.kermit.Logger
import com.juul.kable.CentralManager
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.WriteType
import com.juul.kable.toIdentifier

private object AppleKableCentralManagerConfiguration {
    init {
        try {
            CentralManager.configure { stateRestoration = true }
        } catch (e: IllegalStateException) {
            Logger.w(e) { "Kable CentralManager was initialized before iOS state restoration could be configured" }
        }
    }

    fun ensureInitialized() = Unit
}

internal actual fun initializePlatformBle() {
    AppleKableCentralManagerConfiguration.ensureInitialized()
}

internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // CoreBluetooth owns reconnect scheduling; Kable has no Apple auto-connect builder option.
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral {
    initializePlatformBle()
    return com.juul.kable.Peripheral(address.toIdentifier(), builderAction)
}

internal actual suspend fun Peripheral.negotiatedMaxWriteLength(writeType: BleWriteType): Int? =
    maximumWriteValueLengthForType(
        when (writeType) {
            BleWriteType.WITH_RESPONSE -> WriteType.WithResponse
            BleWriteType.WITHOUT_RESPONSE -> WriteType.WithoutResponse
        },
    )
        .takeIf { it > 0 }

internal actual fun Peripheral.requestHighConnectionPriority(): Boolean = false
