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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.ios.runtime.resources.ios_available
import com.ntsocial.meshlink.ios.runtime.resources.ios_bluetooth_enabled
import com.ntsocial.meshlink.ios.runtime.resources.ios_bluetooth_permission
import com.ntsocial.meshlink.ios.runtime.resources.ios_connect
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_connected
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_connecting
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_device_sleep
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_disconnected
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_heading
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_progress
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_state
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_summary
import com.ntsocial.meshlink.ios.runtime.resources.ios_disconnect
import com.ntsocial.meshlink.ios.runtime.resources.ios_discovered_radios
import com.ntsocial.meshlink.ios.runtime.resources.ios_error
import com.ntsocial.meshlink.ios.runtime.resources.ios_forget_radio
import com.ntsocial.meshlink.ios.runtime.resources.ios_granted
import com.ntsocial.meshlink.ios.runtime.resources.ios_no_radio_selected
import com.ntsocial.meshlink.ios.runtime.resources.ios_no_radios_found
import com.ntsocial.meshlink.ios.runtime.resources.ios_not_granted
import com.ntsocial.meshlink.ios.runtime.resources.ios_off
import com.ntsocial.meshlink.ios.runtime.resources.ios_on
import com.ntsocial.meshlink.ios.runtime.resources.ios_radio_selection
import com.ntsocial.meshlink.ios.runtime.resources.ios_radio_ui_runtime
import com.ntsocial.meshlink.ios.runtime.resources.ios_refresh_bluetooth
import com.ntsocial.meshlink.ios.runtime.resources.ios_rssi_unavailable
import com.ntsocial.meshlink.ios.runtime.resources.ios_rssi_value
import com.ntsocial.meshlink.ios.runtime.resources.ios_scan_in_progress
import com.ntsocial.meshlink.ios.runtime.resources.ios_scan_start
import com.ntsocial.meshlink.ios.runtime.resources.ios_scan_stop
import com.ntsocial.meshlink.ios.runtime.resources.ios_selected_radio
import com.ntsocial.meshlink.ios.runtime.resources.ios_unavailable
import com.ntsocial.meshlink.ios.runtime.resources.ios_unnamed_radio
import org.jetbrains.compose.resources.stringResource
import com.ntsocial.meshlink.ios.runtime.resources.Res as IosRes

@Composable
@Suppress("LongParameterList")
internal fun ConnectionScreen(
    state: RadioUiState,
    onRefreshBluetooth: () -> Unit,
    onToggleScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    ShellScreenColumn {
        Text(
            text = stringResource(IosRes.string.ios_connection_heading),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(IosRes.string.ios_connection_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BluetoothReadinessCard(state)
        RadioStatusCard(state = state, onDisconnect = onDisconnect, onForget = onForget)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRefreshBluetooth, enabled = state.available, modifier = Modifier.weight(1F)) {
                Text(stringResource(IosRes.string.ios_refresh_bluetooth))
            }
            Button(
                onClick = onToggleScan,
                enabled = state.available && state.hasBluetoothPermission && state.bluetoothEnabled,
                modifier = Modifier.weight(1F),
            ) {
                Text(stringResource(if (state.scanning) IosRes.string.ios_scan_stop else IosRes.string.ios_scan_start))
            }
        }

        if (state.scanning) {
            Text(
                text = stringResource(IosRes.string.ios_scan_in_progress),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = stringResource(IosRes.string.ios_discovered_radios),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.devices.isEmpty()) {
            Text(
                text = stringResource(IosRes.string.ios_no_radios_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.devices.forEach { device ->
                DiscoveredRadioButton(
                    device = device,
                    selected = device.peripheralId.equals(state.selectedPeripheralId, ignoreCase = true),
                    enabled = state.hasBluetoothPermission && state.bluetoothEnabled,
                    onConnect = onConnect,
                )
            }
        }

        state.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
            StatusNotice(text = stringResource(IosRes.string.ios_error, error), positive = false)
        }
    }
}

@Composable
private fun BluetoothReadinessCard(state: RadioUiState) {
    ElevatedCard(colors = shellCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            StatusRow(
                label = stringResource(IosRes.string.ios_radio_ui_runtime),
                value =
                stringResource(if (state.available) IosRes.string.ios_available else IosRes.string.ios_unavailable),
                positive = state.available,
            )
            HorizontalDivider()
            BooleanStatusRow(
                label = stringResource(IosRes.string.ios_bluetooth_permission),
                value = state.hasBluetoothPermission,
                positiveLabel = stringResource(IosRes.string.ios_granted),
                negativeLabel = stringResource(IosRes.string.ios_not_granted),
            )
            HorizontalDivider()
            BooleanStatusRow(
                label = stringResource(IosRes.string.ios_bluetooth_enabled),
                value = state.bluetoothEnabled,
                positiveLabel = stringResource(IosRes.string.ios_on),
                negativeLabel = stringResource(IosRes.string.ios_off),
            )
        }
    }
}

@Composable
private fun RadioStatusCard(state: RadioUiState, onDisconnect: () -> Unit, onForget: () -> Unit) {
    ElevatedCard(colors = shellCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            StatusRow(
                label = stringResource(IosRes.string.ios_radio_selection),
                value = state.selectedPeripheralId ?: stringResource(IosRes.string.ios_no_radio_selected),
                positive = if (state.selectedPeripheralId == null) null else true,
            )
            HorizontalDivider()
            StatusRow(
                label = stringResource(IosRes.string.ios_connection_state),
                value = connectionStateLabel(state.connectionState),
                positive =
                when (state.connectionState) {
                    ConnectionState.Connected -> true

                    ConnectionState.Disconnected -> false

                    ConnectionState.Connecting,
                    ConnectionState.DeviceSleep,
                    -> null
                },
            )
            state.connectionProgress?.takeIf(String::isNotBlank)?.let { progress ->
                HorizontalDivider()
                StatusRow(
                    label = stringResource(IosRes.string.ios_connection_progress),
                    value = progress,
                    positive = null,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled =
                    state.selectedPeripheralId != null && state.connectionState != ConnectionState.Disconnected,
                    modifier = Modifier.weight(1F),
                ) {
                    Text(stringResource(IosRes.string.ios_disconnect))
                }
                OutlinedButton(
                    onClick = onForget,
                    enabled = state.selectedPeripheralId != null,
                    modifier = Modifier.weight(1F),
                ) {
                    Text(stringResource(IosRes.string.ios_forget_radio))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRadioButton(
    device: RadioUiDevice,
    selected: Boolean,
    enabled: Boolean,
    onConnect: (String) -> Unit,
) {
    OutlinedButton(
        onClick = { onConnect(device.peripheralId) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(
                text = device.name ?: stringResource(IosRes.string.ios_unnamed_radio),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (selected) {
                Text(
                    text = stringResource(IosRes.string.ios_selected_radio),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = device.peripheralId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                device.rssi?.let { value -> stringResource(IosRes.string.ios_rssi_value, value) }
                    ?: stringResource(IosRes.string.ios_rssi_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(IosRes.string.ios_connect),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun connectionStateLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.Disconnected -> IosRes.string.ios_connection_disconnected
        ConnectionState.Connecting -> IosRes.string.ios_connection_connecting
        ConnectionState.Connected -> IosRes.string.ios_connection_connected
        ConnectionState.DeviceSleep -> IosRes.string.ios_connection_device_sleep
    },
)
