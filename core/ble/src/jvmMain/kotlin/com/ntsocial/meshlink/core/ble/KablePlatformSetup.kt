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

import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.toIdentifier

internal actual fun initializePlatformBle() = Unit

internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // Desktop Kable uses direct connections without needing autoConnect.
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    com.juul.kable.Peripheral(address.toIdentifier(), builderAction)

// JVM/desktop Kable does not expose an MTU StateFlow; return a reasonable default (512)
// so callers can size their writes without falling back to an overly conservative minimum.
internal actual suspend fun Peripheral.negotiatedMaxWriteLength(writeType: BleWriteType): Int? = DEFAULT_JVM_MTU

internal actual fun Peripheral.requestHighConnectionPriority(): Boolean = false

private const val DEFAULT_JVM_MTU = 512
