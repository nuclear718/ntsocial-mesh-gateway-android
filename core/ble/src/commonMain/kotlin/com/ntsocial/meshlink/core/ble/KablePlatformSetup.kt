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

/** Initializes platform BLE facilities before Kable creates its central manager or a peripheral. */
internal expect fun initializePlatformBle()

/** Platform-specific configuration for the Peripheral builder based on device type. */
internal expect fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean)

/** Platform-specific instantiation of a Peripheral by address. */
internal expect fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral

/**
 * Returns the negotiated maximum write payload length in bytes (i.e. ATT MTU minus the 3-byte ATT header), or `null` if
 * MTU has not yet been negotiated on this platform.
 */
internal expect suspend fun Peripheral.negotiatedMaxWriteLength(writeType: BleWriteType): Int?

/**
 * Requests the highest-throughput BLE connection priority (smallest connection interval) supported by the platform.
 *
 * Returns `true` if the request was issued successfully. On platforms without an equivalent API (JVM/iOS) this is a
 * no-op returning `false`. Used by latency-sensitive flows such as DFU firmware streaming.
 */
internal expect fun Peripheral.requestHighConnectionPriority(): Boolean
