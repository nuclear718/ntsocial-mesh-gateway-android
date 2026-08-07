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

import com.ntsocial.meshlink.core.ble.BluetoothState
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayReadiness
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.repository.RadioSessionState
import okio.Buffer
import okio.ByteString

/** Exact private radio/configuration facts captured when routes are issued and rechecked before durable admission. */
internal data class IosGatewayRadioSessionGuard(
    val sessionEpoch: Long,
    val selectedDeviceAddress: String?,
    val activeDeviceAddress: String?,
    val databaseDeviceAddress: String?,
    val transportConnectionState: ConnectionState,
    val sessionConfigured: Boolean,
    val hasBluetoothPermission: Boolean,
    val bluetoothEnabled: Boolean,
    val appConnectionState: ConnectionState,
    val channelSetFingerprint: ByteString,
) {
    fun readiness(hasChannels: Boolean): AppleGatewayReadiness = when {
        !hasBluetoothPermission || !bluetoothEnabled -> AppleGatewayReadiness.BLUETOOTH_UNAVAILABLE

        selectedDeviceAddress == null || selectedDeviceAddress != activeDeviceAddress ->
            AppleGatewayReadiness.DISCONNECTED

        transportConnectionState == ConnectionState.Connecting -> AppleGatewayReadiness.CONNECTING

        transportConnectionState == ConnectionState.Disconnected ||
            transportConnectionState == ConnectionState.DeviceSleep -> AppleGatewayReadiness.DISCONNECTED

        appConnectionState == ConnectionState.Connecting -> AppleGatewayReadiness.CONNECTING

        appConnectionState == ConnectionState.Disconnected || appConnectionState == ConnectionState.DeviceSleep ->
            AppleGatewayReadiness.DISCONNECTED

        databaseDeviceAddress != selectedDeviceAddress -> AppleGatewayReadiness.CONFIGURING

        !sessionConfigured || !hasChannels -> AppleGatewayReadiness.CONFIGURING

        else -> AppleGatewayReadiness.READY
    }

    fun routingContext(): ByteString = Buffer()
        .writePart("ntsocial-apple-gateway-radio-session-v3")
        .writeLong(sessionEpoch)
        .writeNullablePart(selectedDeviceAddress)
        .writeNullablePart(activeDeviceAddress)
        .writeNullablePart(databaseDeviceAddress)
        .writePart(transportConnectionState.routingValue())
        .writeByte(if (sessionConfigured) 1 else 0)
        .writeByte(if (hasBluetoothPermission) 1 else 0)
        .writeByte(if (bluetoothEnabled) 1 else 0)
        .writePart(appConnectionState.routingValue())
        .writeInt(channelSetFingerprint.size)
        .write(channelSetFingerprint)
        .readByteString()
        .sha256()
}

internal fun iosGatewayRadioSessionGuard(
    session: RadioSessionState,
    databaseDeviceAddress: String?,
    bluetooth: BluetoothState,
    appConnectionState: ConnectionState,
    channelSetFingerprint: ByteString,
): IosGatewayRadioSessionGuard = IosGatewayRadioSessionGuard(
    sessionEpoch = session.epoch,
    selectedDeviceAddress = session.selectedDeviceAddress,
    activeDeviceAddress = session.activeDeviceAddress,
    databaseDeviceAddress = databaseDeviceAddress,
    transportConnectionState = session.transportConnectionState,
    sessionConfigured = session.configured,
    hasBluetoothPermission = bluetooth.hasPermissions,
    bluetoothEnabled = bluetooth.enabled,
    appConnectionState = appConnectionState,
    channelSetFingerprint = channelSetFingerprint,
)

private fun Buffer.writePart(value: String): Buffer {
    val bytes = value.encodeToByteArray()
    return writeInt(bytes.size).write(bytes)
}

private fun Buffer.writeNullablePart(value: String?): Buffer = if (value == null) {
    writeInt(-1)
} else {
    writePart(value)
}

private fun ConnectionState.routingValue(): String = when (this) {
    ConnectionState.Disconnected -> "disconnected"
    ConnectionState.Connecting -> "connecting"
    ConnectionState.Connected -> "connected"
    ConnectionState.DeviceSleep -> "device-sleep"
}
