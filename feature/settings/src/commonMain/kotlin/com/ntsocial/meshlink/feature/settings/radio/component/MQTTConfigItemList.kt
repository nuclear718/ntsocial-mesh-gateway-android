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
@file:Suppress("LongMethod")

package com.ntsocial.meshlink.feature.settings.radio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.MqttConnectionState
import com.ntsocial.meshlink.core.model.MqttProbeStatus
import com.ntsocial.meshlink.core.network.repository.effectiveTlsEnabled
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.address
import com.ntsocial.meshlink.core.resources.encryption_enabled
import com.ntsocial.meshlink.core.resources.json_output_enabled
import com.ntsocial.meshlink.core.resources.map_reporting
import com.ntsocial.meshlink.core.resources.mqtt
import com.ntsocial.meshlink.core.resources.mqtt_config
import com.ntsocial.meshlink.core.resources.mqtt_enabled
import com.ntsocial.meshlink.core.resources.mqtt_probe_dns_failure
import com.ntsocial.meshlink.core.resources.mqtt_probe_other_failure
import com.ntsocial.meshlink.core.resources.mqtt_probe_rejected
import com.ntsocial.meshlink.core.resources.mqtt_probe_running
import com.ntsocial.meshlink.core.resources.mqtt_probe_success
import com.ntsocial.meshlink.core.resources.mqtt_probe_success_with_info
import com.ntsocial.meshlink.core.resources.mqtt_probe_tcp_failure
import com.ntsocial.meshlink.core.resources.mqtt_probe_timeout
import com.ntsocial.meshlink.core.resources.mqtt_probe_tls_failure
import com.ntsocial.meshlink.core.resources.mqtt_status_connected
import com.ntsocial.meshlink.core.resources.mqtt_status_connecting
import com.ntsocial.meshlink.core.resources.mqtt_status_disconnected
import com.ntsocial.meshlink.core.resources.mqtt_status_disconnected_with_reason
import com.ntsocial.meshlink.core.resources.mqtt_status_inactive
import com.ntsocial.meshlink.core.resources.mqtt_status_reconnecting
import com.ntsocial.meshlink.core.resources.mqtt_status_reconnecting_with_attempt
import com.ntsocial.meshlink.core.resources.mqtt_test_connection
import com.ntsocial.meshlink.core.resources.password
import com.ntsocial.meshlink.core.resources.proxy_to_client_enabled
import com.ntsocial.meshlink.core.resources.root_topic
import com.ntsocial.meshlink.core.resources.tls_enabled
import com.ntsocial.meshlink.core.resources.username
import com.ntsocial.meshlink.core.ui.component.EditPasswordPreference
import com.ntsocial.meshlink.core.ui.component.EditTextPreference
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.ModuleConfig

@Composable
fun MQTTConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val mqttProxyState by viewModel.mqttConnectionState.collectAsStateWithLifecycle()
    val probeStatus by viewModel.mqttProbeStatus.collectAsStateWithLifecycle()
    val destNum = destNode?.num
    val mqttConfig = state.moduleConfig.mqtt ?: ModuleConfig.MQTTConfig()
    val formState = rememberConfigState(initialValue = mqttConfig)

    val currentMapReportSettings = formState.value.map_report_settings ?: ModuleConfig.MapReportSettings()
    if (!currentMapReportSettings.should_report_location) {
        val settings =
            currentMapReportSettings.copy(should_report_location = viewModel.shouldReportLocation(destNum).value)
        formState.value = formState.value.copy(map_report_settings = settings)
    }

    val consentValid =
        if (formState.value.map_reporting_enabled) {
            (formState.value.map_report_settings?.should_report_location ?: false) &&
                (formState.value.map_report_settings?.publish_interval_secs ?: 0) >= MIN_INTERVAL_SECS
        } else {
            true
        }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.mqtt),
        onBack = onBack,
        configState = formState,
        enabled = state.connected && consentValid,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(mqtt = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item { MqttStatusRow(mqttProxyState) }

        item {
            TitledCard(title = stringResource(Res.string.mqtt_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.mqtt_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                MqttAddressAndProbe(
                    enabled = state.connected,
                    formState = formState,
                    probeStatus = probeStatus,
                    focusManager = focusManager,
                    onProbe = viewModel::probeMqttConnection,
                    onClearProbe = viewModel::clearMqttProbeStatus,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.username),
                    value = formState.value.username,
                    maxSize = 63, // username max_size:64
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(username = it) },
                )
                HorizontalDivider()
                EditPasswordPreference(
                    title = stringResource(Res.string.password),
                    value = formState.value.password,
                    maxSize = 63, // password max_size:64
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(password = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.encryption_enabled),
                    checked = formState.value.encryption_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(encryption_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.json_output_enabled),
                    checked = formState.value.json_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(json_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val resolvedAddress = formState.value.address.ifEmpty { "mqtt.meshtastic.org" }
                val enforceTls = effectiveTlsEnabled(resolvedAddress, tlsEnabled = false)
                SwitchPreference(
                    title = stringResource(Res.string.tls_enabled),
                    checked = formState.value.tls_enabled || enforceTls,
                    enabled = state.connected && !enforceTls,
                    onCheckedChange = { formState.value = formState.value.copy(tls_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.root_topic),
                    value = formState.value.root,
                    maxSize = 31, // root max_size:32
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(root = it) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.proxy_to_client_enabled),
                    checked = formState.value.proxy_to_client_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(proxy_to_client_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.map_reporting)) {
                val mapReportSettings = formState.value.map_report_settings ?: ModuleConfig.MapReportSettings()
                MapReportingPreference(
                    mapReportingEnabled = formState.value.map_reporting_enabled,
                    onMapReportingEnabledChanged = {
                        formState.value = formState.value.copy(map_reporting_enabled = it)
                    },
                    shouldReportLocation = mapReportSettings.should_report_location,
                    onShouldReportLocationChanged = {
                        viewModel.setShouldReportLocation(destNum, it)
                        val settings = mapReportSettings.copy(should_report_location = it)
                        formState.value = formState.value.copy(map_report_settings = settings)
                    },
                    positionPrecision = mapReportSettings.position_precision,
                    onPositionPrecisionChanged = {
                        val settings = mapReportSettings.copy(position_precision = it)
                        formState.value = formState.value.copy(map_report_settings = settings)
                    },
                    publishIntervalSecs = mapReportSettings.publish_interval_secs,
                    onPublishIntervalSecsChanged = {
                        val settings = mapReportSettings.copy(publish_interval_secs = it)
                        formState.value = formState.value.copy(map_report_settings = settings)
                    },
                    enabled = state.connected,
                )
            }
        }
    }
}

private const val MIN_INTERVAL_SECS = 3600

private val AmberColor = Color(0xFFFFA000)
private val GreenColor = Color(0xFF4CAF50)

@Composable
private fun MqttStatusRow(state: MqttConnectionState) {
    val (label, color) =
        when (state) {
            is MqttConnectionState.Inactive ->
                stringResource(Res.string.mqtt_status_inactive) to MaterialTheme.colorScheme.outline

            is MqttConnectionState.Disconnected -> {
                val text =
                    state.reason?.let { stringResource(Res.string.mqtt_status_disconnected_with_reason, it) }
                        ?: stringResource(Res.string.mqtt_status_disconnected)
                text to MaterialTheme.colorScheme.error
            }

            is MqttConnectionState.Connecting -> stringResource(Res.string.mqtt_status_connecting) to AmberColor

            is MqttConnectionState.Connected -> stringResource(Res.string.mqtt_status_connected) to GreenColor

            is MqttConnectionState.Reconnecting -> {
                val err = state.lastError
                val text =
                    if (err != null) {
                        stringResource(Res.string.mqtt_status_reconnecting_with_attempt, state.attempt, err)
                    } else {
                        stringResource(Res.string.mqtt_status_reconnecting)
                    }
                text to AmberColor
            }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MqttAddressAndProbe(
    enabled: Boolean,
    formState: ConfigState<ModuleConfig.MQTTConfig>,
    probeStatus: MqttProbeStatus?,
    focusManager: FocusManager,
    onProbe: (address: String, tlsEnabled: Boolean, username: String, password: String) -> Unit,
    onClearProbe: () -> Unit,
) {
    EditTextPreference(
        title = stringResource(Res.string.address),
        value = formState.value.address,
        maxSize = 63, // address max_size:64
        enabled = enabled,
        isError = false,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        onValueChanged = {
            formState.value = formState.value.copy(address = it)
            onClearProbe()
        },
    )
    HorizontalDivider()
    MqttProbeRow(
        enabled = enabled && formState.value.address.isNotBlank(),
        status = probeStatus,
        onTestClick = {
            focusManager.clearFocus()
            val resolvedAddress = formState.value.address.ifEmpty { "mqtt.meshtastic.org" }
            val effectiveTls = effectiveTlsEnabled(resolvedAddress, formState.value.tls_enabled)
            onProbe(formState.value.address, effectiveTls, formState.value.username, formState.value.password)
        },
    )
}

@Composable
private fun MqttProbeRow(enabled: Boolean, status: MqttProbeStatus?, onTestClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onTestClick, enabled = enabled && status !is MqttProbeStatus.Probing) {
                Text(stringResource(Res.string.mqtt_test_connection))
            }
            val (probeText, probeColor) = status.toLabel() ?: return@Row
            Text(text = probeText, style = MaterialTheme.typography.bodySmall, color = probeColor)
        }
    }
}

@Composable
private fun MqttProbeStatus?.toLabel(): Pair<String, Color>? = when (this) {
    null -> null

    is MqttProbeStatus.Probing ->
        stringResource(Res.string.mqtt_probe_running) to MaterialTheme.colorScheme.onSurfaceVariant

    is MqttProbeStatus.Success -> {
        val text =
            serverInfo?.let { stringResource(Res.string.mqtt_probe_success_with_info, it) }
                ?: stringResource(Res.string.mqtt_probe_success)
        text to GreenColor
    }

    is MqttProbeStatus.Rejected ->
        stringResource(Res.string.mqtt_probe_rejected, reason ?: reasonCode.toString()) to
            MaterialTheme.colorScheme.error

    is MqttProbeStatus.DnsFailure ->
        stringResource(Res.string.mqtt_probe_dns_failure) to MaterialTheme.colorScheme.error

    is MqttProbeStatus.TcpFailure ->
        stringResource(Res.string.mqtt_probe_tcp_failure) to MaterialTheme.colorScheme.error

    is MqttProbeStatus.TlsFailure ->
        stringResource(Res.string.mqtt_probe_tls_failure, message ?: "unknown") to MaterialTheme.colorScheme.error

    is MqttProbeStatus.Timeout ->
        stringResource(Res.string.mqtt_probe_timeout, timeoutMs.toInt()) to MaterialTheme.colorScheme.error

    is MqttProbeStatus.Other ->
        stringResource(Res.string.mqtt_probe_other_failure) to MaterialTheme.colorScheme.error
}
