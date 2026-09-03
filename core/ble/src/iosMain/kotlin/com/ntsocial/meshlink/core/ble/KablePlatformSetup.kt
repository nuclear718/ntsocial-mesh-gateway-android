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
import kotlinx.atomicfu.atomic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

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

// Kable's CoreBluetooth scanner rejects Filter.Address. The caller still performs the exact identifier comparison
// after CoreBluetooth's native service filter emits an advertisement.
internal actual val platformSupportsBleScanAddressFilter: Boolean = false

internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // CoreBluetooth owns reconnect scheduling; Kable has no Apple auto-connect builder option.
    // Authentication failures from the encrypted FROMNUM subscription are connection failures, not optional
    // observation noise. Propagating them also lets a cancelled or incorrect PIN fail immediately.
    observationExceptionHandler { cause -> throw cause }
}

private val IOS_FIRST_PAIRING_PROFILE_TIMEOUT = 90.seconds

internal actual fun platformProfileSetupTimeout(serviceUuid: Uuid, requested: Duration): Duration =
    if (serviceUuid == MeshtasticBleConstants.SERVICE_UUID && requested < IOS_FIRST_PAIRING_PROFILE_TIMEOUT) {
        IOS_FIRST_PAIRING_PROFILE_TIMEOUT
    } else {
        requested
    }

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral {
    initializePlatformBle()
    return com.juul.kable.Peripheral(address.toIdentifier(), builderAction)
}

private val preparedPeripherals = atomic<Map<String, PlatformPreparedPeripheral>>(emptyMap())

internal fun replacePlatformPreparedPeripheral(
    address: String,
    prepared: PlatformPreparedPeripheral,
): PlatformPreparedPeripheral? {
    val key = address.lowercase()
    while (true) {
        val current = preparedPeripherals.value
        val previous = current[key]
        if (preparedPeripherals.compareAndSet(current, current + (key to prepared))) return previous
    }
}

private fun removeOwnedPlatformPreparedPeripheral(device: BleDevice): PlatformPreparedPeripheral? {
    val key = device.address.lowercase()
    while (true) {
        val current = preparedPeripherals.value
        val prepared = current[key] ?: return null
        if (prepared.owner !== device) return null
        if (preparedPeripherals.compareAndSet(current, current - key)) return prepared
    }
}

internal actual fun takePlatformPreparedPeripheral(device: BleDevice): PlatformPreparedPeripheral? =
    removeOwnedPlatformPreparedPeripheral(device)

internal fun discardPlatformPreparedPeripheral(device: BleDevice): PlatformPreparedPeripheral? =
    removeOwnedPlatformPreparedPeripheral(device)

internal actual suspend fun Peripheral.negotiatedMaxWriteLength(writeType: BleWriteType): Int? =
    maximumWriteValueLengthForType(
        when (writeType) {
            BleWriteType.WITH_RESPONSE -> WriteType.WithResponse
            BleWriteType.WITHOUT_RESPONSE -> WriteType.WithoutResponse
        },
    )
        .takeIf { it > 0 }

internal actual fun Peripheral.requestHighConnectionPriority(): Boolean = false
