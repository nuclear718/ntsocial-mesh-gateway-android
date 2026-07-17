/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.feature.meshcore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.common.util.NumberFormatter
import com.ntsocial.meshlink.core.meshcore.MeshCoreConnectionState
import com.ntsocial.meshlink.core.meshcore.MeshCoreRadioSettings
import com.ntsocial.meshlink.core.meshcore.MeshCoreTransport
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_bluetooth
import com.ntsocial.meshlink.core.resources.ic_hub
import com.ntsocial.meshlink.core.resources.ic_router
import com.ntsocial.meshlink.core.resources.ic_settings_input_antenna
import com.ntsocial.meshlink.core.resources.ic_usb
import com.ntsocial.meshlink.core.resources.ic_wifi
import com.ntsocial.meshlink.core.resources.meshcore_bandwidth
import com.ntsocial.meshlink.core.resources.meshcore_bandwidth_value
import com.ntsocial.meshlink.core.resources.meshcore_ble
import com.ntsocial.meshlink.core.resources.meshcore_coding_rate
import com.ntsocial.meshlink.core.resources.meshcore_coding_rate_value
import com.ntsocial.meshlink.core.resources.meshcore_connected
import com.ntsocial.meshlink.core.resources.meshcore_connecting
import com.ntsocial.meshlink.core.resources.meshcore_device
import com.ntsocial.meshlink.core.resources.meshcore_disconnected
import com.ntsocial.meshlink.core.resources.meshcore_error
import com.ntsocial.meshlink.core.resources.meshcore_frequency
import com.ntsocial.meshlink.core.resources.meshcore_frequency_value
import com.ntsocial.meshlink.core.resources.meshcore_identity
import com.ntsocial.meshlink.core.resources.meshcore_protocol_version
import com.ntsocial.meshlink.core.resources.meshcore_protocol_version_value
import com.ntsocial.meshlink.core.resources.meshcore_radio_settings
import com.ntsocial.meshlink.core.resources.meshcore_scanning
import com.ntsocial.meshlink.core.resources.meshcore_setting_unavailable
import com.ntsocial.meshlink.core.resources.meshcore_spreading_factor
import com.ntsocial.meshlink.core.resources.meshcore_spreading_factor_value
import com.ntsocial.meshlink.core.resources.meshcore_supported_transports
import com.ntsocial.meshlink.core.resources.meshcore_synchronizing
import com.ntsocial.meshlink.core.resources.meshcore_tcp
import com.ntsocial.meshlink.core.resources.meshcore_tx_power
import com.ntsocial.meshlink.core.resources.meshcore_tx_power_value
import com.ntsocial.meshlink.core.resources.meshcore_usb
import com.ntsocial.meshlink.core.ui.component.AdaptiveTwoPane
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MeshCoreRadioPanel(state: MeshCoreUiState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        AdaptiveTwoPane(
            first = { MeshCoreDeviceCards(state) },
            second = { MeshCoreRadioSettingsCard(state.selfInfo?.radio) },
        )
    }
}

@Composable
private fun MeshCoreDeviceCards(state: MeshCoreUiState) {
    Column {
        MeshCoreSectionCard(title = stringResource(Res.string.meshcore_device)) {
            MeshCoreInfoRow(
                icon = Res.drawable.ic_hub,
                label = stringResource(Res.string.meshcore_identity),
                value = state.selfInfo?.name ?: stringResource(Res.string.meshcore_setting_unavailable),
            )
            MeshCoreInfoDivider()
            MeshCoreInfoRow(
                icon = Res.drawable.ic_router,
                label = stringResource(Res.string.meshcore_device),
                value = state.deviceInfo?.model ?: stringResource(Res.string.meshcore_setting_unavailable),
            )
            MeshCoreInfoDivider()
            MeshCoreInfoRow(
                icon = Res.drawable.ic_settings_input_antenna,
                label = stringResource(Res.string.meshcore_protocol_version),
                value =
                state.deviceInfo?.protocolVersion?.let {
                    stringResource(Res.string.meshcore_protocol_version_value, it)
                } ?: stringResource(Res.string.meshcore_setting_unavailable),
            )
        }
        MeshCoreSectionCard(title = stringResource(Res.string.meshcore_supported_transports)) {
            MeshCoreConnectionBanner(state.connectionState)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MeshCoreTransport.entries.forEach { transport -> MeshCoreTransportBadge(transport) }
            }
        }
    }
}

@Composable
private fun MeshCoreRadioSettingsCard(radio: MeshCoreRadioSettings?) {
    MeshCoreSectionCard(title = stringResource(Res.string.meshcore_radio_settings)) {
        MeshCoreInfoRow(
            Res.drawable.ic_settings_input_antenna,
            stringResource(Res.string.meshcore_frequency),
            radio?.let {
                stringResource(
                    Res.string.meshcore_frequency_value,
                    NumberFormatter.format(it.frequencyKhz / KHZ_PER_MHZ, FREQUENCY_DECIMALS),
                )
            } ?: stringResource(Res.string.meshcore_setting_unavailable),
        )
        MeshCoreInfoDivider()
        MeshCoreInfoRow(
            Res.drawable.ic_wifi,
            stringResource(Res.string.meshcore_bandwidth),
            radio?.let {
                stringResource(
                    Res.string.meshcore_bandwidth_value,
                    NumberFormatter.format(it.bandwidthHz / HZ_PER_KHZ, BANDWIDTH_DECIMALS),
                )
            } ?: stringResource(Res.string.meshcore_setting_unavailable),
        )
        MeshCoreInfoDivider()
        MeshCoreInfoRow(
            Res.drawable.ic_settings_input_antenna,
            stringResource(Res.string.meshcore_spreading_factor),
            radio?.let { stringResource(Res.string.meshcore_spreading_factor_value, it.spreadingFactor) }
                ?: stringResource(Res.string.meshcore_setting_unavailable),
        )
        MeshCoreInfoDivider()
        MeshCoreInfoRow(
            Res.drawable.ic_settings_input_antenna,
            stringResource(Res.string.meshcore_coding_rate),
            radio?.let { stringResource(Res.string.meshcore_coding_rate_value, it.codingRate) }
                ?: stringResource(Res.string.meshcore_setting_unavailable),
        )
        MeshCoreInfoDivider()
        MeshCoreInfoRow(
            Res.drawable.ic_settings_input_antenna,
            stringResource(Res.string.meshcore_tx_power),
            radio?.let { stringResource(Res.string.meshcore_tx_power_value, it.txPowerDbm) }
                ?: stringResource(Res.string.meshcore_setting_unavailable),
        )
    }
}

@Composable
private fun MeshCoreConnectionBanner(connectionState: MeshCoreConnectionState) {
    val containerColor =
        when (connectionState) {
            MeshCoreConnectionState.CONNECTED -> MaterialTheme.colorScheme.secondaryContainer
            MeshCoreConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
            MeshCoreConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.tertiaryContainer
        }
    val contentColor =
        when (connectionState) {
            MeshCoreConnectionState.CONNECTED -> MaterialTheme.colorScheme.onSecondaryContainer
            MeshCoreConnectionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            MeshCoreConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_hub),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(connectionState.labelResource()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MeshCoreInfoRow(icon: DrawableResource, label: String, value: String) {
    MeshCoreListRow(icon = icon, title = label, subtitle = value)
}

@Composable
private fun MeshCoreInfoDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun MeshCoreTransportBadge(transport: MeshCoreTransport) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(transport.iconResource()),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(transport.labelResource()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun MeshCoreConnectionState.labelResource() = when (this) {
    MeshCoreConnectionState.DISCONNECTED -> Res.string.meshcore_disconnected
    MeshCoreConnectionState.SCANNING -> Res.string.meshcore_scanning
    MeshCoreConnectionState.CONNECTING -> Res.string.meshcore_connecting
    MeshCoreConnectionState.SYNCHRONIZING -> Res.string.meshcore_synchronizing
    MeshCoreConnectionState.CONNECTED -> Res.string.meshcore_connected
    MeshCoreConnectionState.ERROR -> Res.string.meshcore_error
}

private fun MeshCoreTransport.labelResource() = when (this) {
    MeshCoreTransport.BLE -> Res.string.meshcore_ble
    MeshCoreTransport.USB -> Res.string.meshcore_usb
    MeshCoreTransport.TCP -> Res.string.meshcore_tcp
}

private fun MeshCoreTransport.iconResource() = when (this) {
    MeshCoreTransport.BLE -> Res.drawable.ic_bluetooth
    MeshCoreTransport.USB -> Res.drawable.ic_usb
    MeshCoreTransport.TCP -> Res.drawable.ic_wifi
}

private const val KHZ_PER_MHZ = 1000.0
private const val HZ_PER_KHZ = 1000.0
private const val FREQUENCY_DECIMALS = 3
private const val BANDWIDTH_DECIMALS = 2
