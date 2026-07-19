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

/** No-op stubs for iOS target in core:ble. */
internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // No-op for stubs
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    throw UnsupportedOperationException("iOS Peripheral not yet implemented")

internal actual fun Peripheral.negotiatedMaxWriteLength(): Int? = null

internal actual fun Peripheral.requestHighConnectionPriority(): Boolean = false
