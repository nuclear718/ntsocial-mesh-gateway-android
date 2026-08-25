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
package com.ntsocial.meshlink.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.bluetooth
import com.ntsocial.meshlink.core.resources.no_bluetooth_devices_hint
import com.ntsocial.meshlink.core.resources.no_bluetooth_devices_seen
import com.ntsocial.meshlink.core.resources.scan_bluetooth_devices
import com.ntsocial.meshlink.core.resources.scanning_bluetooth
import com.ntsocial.meshlink.core.ui.icon.Bluetooth
import com.ntsocial.meshlink.core.ui.icon.Close
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Search
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import org.jetbrains.compose.resources.stringResource

/** Bluetooth-only device list for the first-release connection UI. */
@Composable
fun BluetoothDeviceList(
    connectionState: ConnectionState,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry.Ble>,
    isBleScanning: Boolean,
    onSelectDevice: (DeviceListEntry.Ble) -> Unit,
    onToggleBleScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        bluetoothSection(
            bleDevices = bleDevices,
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            isBleScanning = isBleScanning,
            onSelectDevice = onSelectDevice,
            onToggleBleScan = onToggleBleScan,
        )
        item(key = "spacer:bottom", contentType = "spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.bluetoothSection(
    bleDevices: List<DeviceListEntry.Ble>,
    connectionState: ConnectionState,
    selectedDevice: String,
    isBleScanning: Boolean,
    onSelectDevice: (DeviceListEntry.Ble) -> Unit,
    onToggleBleScan: () -> Unit,
) {
    item(key = "header:ble", contentType = "header") {
        DeviceSectionHeader(
            title = stringResource(Res.string.bluetooth),
            trailing = {
                ScanToggleAction(
                    isScanning = isBleScanning,
                    scanLabel = stringResource(Res.string.scan_bluetooth_devices),
                    scanningLabel = stringResource(Res.string.scanning_bluetooth),
                    onToggle = onToggleBleScan,
                )
            },
        )
    }
    items(bleDevices, key = { device -> "ble:${device.fullAddress}" }, contentType = { "device" }) { device ->
        DeviceCard(
            device = device,
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            onSelect = onSelectDevice,
            modifier = Modifier.animateItem(),
        )
    }
    if (bleDevices.isEmpty()) {
        item(key = "empty:ble", contentType = "empty") {
            SectionEmptyState(
                text = stringResource(Res.string.no_bluetooth_devices_seen),
                supportingText = stringResource(Res.string.no_bluetooth_devices_hint),
                imageVector = MeshtasticIcons.Bluetooth,
            )
        }
    }
}

/** Single device row: card + [DeviceListItem]. Factored out so every section renders items identically. */
@Composable
private fun DeviceCard(
    device: DeviceListEntry.Ble,
    connectionState: ConnectionState,
    selectedDevice: String,
    onSelect: (DeviceListEntry.Ble) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        DeviceListItem(
            connectionState =
            connectionState.takeIf { device.fullAddress == selectedDevice } ?: ConnectionState.Disconnected,
            device = device,
            onSelect = { onSelect(device) },
            rssi = device.device.rssi,
        )
    }
}

/** Compact text-button variant of the scan toggle, used inside a section header's trailing slot. */
@Composable
private fun ScanToggleAction(isScanning: Boolean, scanLabel: String, scanningLabel: String, onToggle: () -> Unit) {
    ConnectionActionButton(
        onClick = onToggle,
        icon = if (isScanning) MeshtasticIcons.Close else MeshtasticIcons.Search,
        text = if (isScanning) scanningLabel else scanLabel,
        style = ConnectionActionButtonStyle.Text,
    )
}

/**
 * Inline empty state for an individual transport section. Follows Material 3 inline empty-state guidance: a small,
 * muted icon, a short title, and an optional supporting hint. Rendered within the section's flow (no full-page
 * takeover); encourages the user to act via the section header's scan toggle rather than duplicating action buttons.
 */
@Composable
private fun SectionEmptyState(
    text: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
