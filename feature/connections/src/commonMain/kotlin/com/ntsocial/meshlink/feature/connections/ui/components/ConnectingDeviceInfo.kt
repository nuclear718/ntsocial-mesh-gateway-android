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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.connected
import com.ntsocial.meshlink.core.resources.connected_sleeping
import com.ntsocial.meshlink.core.resources.connecting
import com.ntsocial.meshlink.core.resources.must_set_region
import com.ntsocial.meshlink.core.resources.not_connected
import com.ntsocial.meshlink.core.ui.viewmodel.ConnectionStatus
import org.jetbrains.compose.resources.stringResource

/**
 * Displays the currently connecting (or connected) device with its name, address, connection status, and a disconnect
 * button.
 */
@Composable
fun ConnectingDeviceInfo(
    deviceName: String,
    deviceAddress: String,
    connectionStatus: ConnectionStatus,
    connectionProgress: String?,
    onClickDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel =
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> stringResource(Res.string.connected)
            ConnectionStatus.MUST_SET_REGION -> stringResource(Res.string.must_set_region)
            ConnectionStatus.CONNECTING -> connectionProgress ?: stringResource(Res.string.connecting)
            ConnectionStatus.CONNECTED_SLEEPING -> stringResource(Res.string.connected_sleeping)
            ConnectionStatus.NOT_CONNECTED -> stringResource(Res.string.not_connected)
        }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))

            Column {
                Text(text = deviceName, style = MaterialTheme.typography.headlineSmall)
                Text(text = deviceAddress, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DisconnectButton(onClick = onClickDisconnect)
    }
}
