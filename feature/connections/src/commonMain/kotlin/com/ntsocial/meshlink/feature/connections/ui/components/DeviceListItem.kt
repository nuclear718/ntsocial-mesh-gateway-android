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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.action_select_device
import com.ntsocial.meshlink.core.resources.add
import com.ntsocial.meshlink.core.resources.bluetooth
import com.ntsocial.meshlink.core.resources.network
import com.ntsocial.meshlink.core.resources.serial
import com.ntsocial.meshlink.core.ui.component.NodeChip
import com.ntsocial.meshlink.core.ui.component.Rssi
import com.ntsocial.meshlink.core.ui.icon.Add
import com.ntsocial.meshlink.core.ui.icon.Bluetooth
import com.ntsocial.meshlink.core.ui.icon.BluetoothConnected
import com.ntsocial.meshlink.core.ui.icon.BluetoothSearching
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Usb
import com.ntsocial.meshlink.core.ui.icon.Wifi
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val RSSI_UPDATE_RATE_MS = 2000L

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun DeviceListItem(
    connectionState: ConnectionState,
    device: DeviceListEntry,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    rssi: Int? = null,
) {
    // Throttle the RSSI updates to match the connected device polling rate
    var displayedRssi by remember { mutableIntStateOf(rssi ?: 0) }
    val currentRssi by rememberUpdatedState(rssi)
    LaunchedEffect(Unit) {
        while (true) {
            delay(RSSI_UPDATE_RATE_MS)
            displayedRssi = currentRssi ?: 0
        }
    }

    val icon =
        when (device) {
            is DeviceListEntry.Ble ->
                if (connectionState is ConnectionState.Connected) {
                    MeshtasticIcons.BluetoothConnected
                } else if (connectionState is ConnectionState.Connecting) {
                    MeshtasticIcons.BluetoothSearching
                } else {
                    MeshtasticIcons.Bluetooth
                }

            is DeviceListEntry.Usb -> MeshtasticIcons.Usb

            is DeviceListEntry.Tcp -> MeshtasticIcons.Wifi

            is DeviceListEntry.Mock -> MeshtasticIcons.Add
        }

    val contentDescription =
        when (device) {
            is DeviceListEntry.Ble -> stringResource(Res.string.bluetooth)
            is DeviceListEntry.Usb -> stringResource(Res.string.serial)
            is DeviceListEntry.Tcp -> stringResource(Res.string.network)
            is DeviceListEntry.Mock -> stringResource(Res.string.add)
        }

    val selectLabel = stringResource(Res.string.action_select_device)
    val isSelected = connectionState is ConnectionState.Connected
    val clickableModifier =
        if (onDelete != null) {
            Modifier.semantics { selected = isSelected }
                .combinedClickable(
                    onClickLabel = selectLabel,
                    role = Role.RadioButton,
                    onClick = onSelect,
                    onLongClick = onDelete,
                )
        } else {
            Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
        }

    val iconTint =
        if (connectionState is ConnectionState.Connected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    ListItem(
        modifier = modifier.fillMaxWidth().then(clickableModifier).padding(vertical = 4.dp),
        headlineContent = { DeviceHeadline(device = device) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp),
                tint = iconTint,
            )
        },
        supportingContent = { Text(text = device.address, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rssi != null) {
                    Rssi(rssi = displayedRssi)
                }

                if (connectionState is ConnectionState.Connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    RadioButton(selected = connectionState is ConnectionState.Connected, onClick = null)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * Headline for a device row. When we have a [DeviceListEntry.node] in the local DB (i.e. we've previously connected and
 * learned the device's mesh identity), render the colored [NodeChip] + the node's long name so users can visually
 * identify the device at a glance. Otherwise fall back to the raw advertised device name.
 */
@Composable
private fun DeviceHeadline(device: DeviceListEntry) {
    val node = device.node
    if (node != null) {
        NodeChip(node = node)
    } else {
        Text(
            text = device.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
