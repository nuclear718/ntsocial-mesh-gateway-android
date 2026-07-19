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
package com.ntsocial.meshlink.feature.settings.radio.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.util.isDebug
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.accept
import com.ntsocial.meshlink.core.resources.are_you_sure
import com.ntsocial.meshlink.core.resources.button_gpio
import com.ntsocial.meshlink.core.resources.buzzer_gpio
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.clear_time_zone
import com.ntsocial.meshlink.core.resources.config_device_doubleTapAsButtonPress_summary
import com.ntsocial.meshlink.core.resources.config_device_ledHeartbeatEnabled_summary
import com.ntsocial.meshlink.core.resources.config_device_tripleClickAsAdHocPing_summary
import com.ntsocial.meshlink.core.resources.config_device_tzdef_summary
import com.ntsocial.meshlink.core.resources.config_device_use_phone_tz
import com.ntsocial.meshlink.core.resources.device
import com.ntsocial.meshlink.core.resources.device_storage_ui_title
import com.ntsocial.meshlink.core.resources.device_theme_language
import com.ntsocial.meshlink.core.resources.double_tap_as_button_press
import com.ntsocial.meshlink.core.resources.file_entry
import com.ntsocial.meshlink.core.resources.files_available
import com.ntsocial.meshlink.core.resources.gpio
import com.ntsocial.meshlink.core.resources.hardware
import com.ntsocial.meshlink.core.resources.i_know_what_i_m_doing
import com.ntsocial.meshlink.core.resources.led_heartbeat
import com.ntsocial.meshlink.core.resources.no_files_manifested
import com.ntsocial.meshlink.core.resources.nodeinfo_broadcast_interval
import com.ntsocial.meshlink.core.resources.options
import com.ntsocial.meshlink.core.resources.rebroadcast_mode
import com.ntsocial.meshlink.core.resources.rebroadcast_mode_all_desc
import com.ntsocial.meshlink.core.resources.rebroadcast_mode_all_skip_decoding_desc
import com.ntsocial.meshlink.core.resources.rebroadcast_mode_core_portnums_only_desc
import com.ntsocial.meshlink.core.resources.rebroadcast_mode_known_only_desc
import com.ntsocial.meshlink.core.resources.rebroadcast_mode_local_only_desc
import com.ntsocial.meshlink.core.resources.rebroadcast_mode_none_desc
import com.ntsocial.meshlink.core.resources.role
import com.ntsocial.meshlink.core.resources.role_client_base_desc
import com.ntsocial.meshlink.core.resources.role_client_desc
import com.ntsocial.meshlink.core.resources.role_client_hidden_desc
import com.ntsocial.meshlink.core.resources.role_client_mute_desc
import com.ntsocial.meshlink.core.resources.role_lost_and_found_desc
import com.ntsocial.meshlink.core.resources.role_repeater_desc
import com.ntsocial.meshlink.core.resources.role_router_client_desc
import com.ntsocial.meshlink.core.resources.role_router_desc
import com.ntsocial.meshlink.core.resources.role_router_late_desc
import com.ntsocial.meshlink.core.resources.role_sensor_desc
import com.ntsocial.meshlink.core.resources.role_tak_desc
import com.ntsocial.meshlink.core.resources.role_tak_tracker_desc
import com.ntsocial.meshlink.core.resources.role_tracker_desc
import com.ntsocial.meshlink.core.resources.router_role_confirmation_text
import com.ntsocial.meshlink.core.resources.time_zone
import com.ntsocial.meshlink.core.resources.triple_click_adhoc_ping
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.EditTextPreference
import com.ntsocial.meshlink.core.ui.component.InsetDivider
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.core.ui.icon.Close
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.PhoneAndroid
import com.ntsocial.meshlink.core.ui.icon.role
import com.ntsocial.meshlink.core.ui.util.annotatedStringFromHtml
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.util.IntervalConfiguration
import com.ntsocial.meshlink.feature.settings.util.toDisplayString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@Composable expect fun rememberSystemTimeZonePosixString(): String

@Suppress("DEPRECATION")
private val Config.DeviceConfig.Role.description: StringResource
    get() =
        when (this) {
            Config.DeviceConfig.Role.CLIENT -> Res.string.role_client_desc
            Config.DeviceConfig.Role.CLIENT_BASE -> Res.string.role_client_base_desc
            Config.DeviceConfig.Role.CLIENT_MUTE -> Res.string.role_client_mute_desc
            Config.DeviceConfig.Role.ROUTER -> Res.string.role_router_desc
            Config.DeviceConfig.Role.ROUTER_CLIENT -> Res.string.role_router_client_desc
            Config.DeviceConfig.Role.REPEATER -> Res.string.role_repeater_desc
            Config.DeviceConfig.Role.TRACKER -> Res.string.role_tracker_desc
            Config.DeviceConfig.Role.SENSOR -> Res.string.role_sensor_desc
            Config.DeviceConfig.Role.TAK -> Res.string.role_tak_desc
            Config.DeviceConfig.Role.CLIENT_HIDDEN -> Res.string.role_client_hidden_desc
            Config.DeviceConfig.Role.LOST_AND_FOUND -> Res.string.role_lost_and_found_desc
            Config.DeviceConfig.Role.TAK_TRACKER -> Res.string.role_tak_tracker_desc
            Config.DeviceConfig.Role.ROUTER_LATE -> Res.string.role_router_late_desc
        }

private val Config.DeviceConfig.RebroadcastMode.description: StringResource
    get() =
        when (this) {
            Config.DeviceConfig.RebroadcastMode.ALL -> Res.string.rebroadcast_mode_all_desc

            Config.DeviceConfig.RebroadcastMode.ALL_SKIP_DECODING -> Res.string.rebroadcast_mode_all_skip_decoding_desc

            Config.DeviceConfig.RebroadcastMode.LOCAL_ONLY -> Res.string.rebroadcast_mode_local_only_desc

            Config.DeviceConfig.RebroadcastMode.KNOWN_ONLY -> Res.string.rebroadcast_mode_known_only_desc

            Config.DeviceConfig.RebroadcastMode.NONE -> Res.string.rebroadcast_mode_none_desc

            Config.DeviceConfig.RebroadcastMode.CORE_PORTNUMS_ONLY ->
                Res.string.rebroadcast_mode_core_portnums_only_desc
        }

@Suppress("DEPRECATION", "LongMethod")
@Composable
fun DeviceConfigScreenCommon(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val deviceConfig = state.radioConfig.device ?: Config.DeviceConfig()
    val formState = rememberConfigState(initialValue = deviceConfig)
    var selectedRole by rememberSaveable(formState.value.role) { mutableStateOf(formState.value.role) }
    val infrastructureRoles =
        listOf(Config.DeviceConfig.Role.ROUTER, Config.DeviceConfig.Role.ROUTER_LATE, Config.DeviceConfig.Role.REPEATER)
    if (selectedRole != formState.value.role) {
        if (selectedRole in infrastructureRoles) {
            RouterRoleConfirmationDialog(
                onDismiss = { selectedRole = formState.value.role },
                onConfirm = { formState.value = formState.value.copy(role = selectedRole) },
            )
        } else {
            formState.value = formState.value.copy(role = selectedRole)
        }
    }
    val focusManager = LocalFocusManager.current
    RadioConfigScreenList(
        title = stringResource(Res.string.device),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config(device = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.options)) {
                val currentRole = formState.value.role
                DropDownPreference(
                    title = stringResource(Res.string.role),
                    enabled = state.connected,
                    selectedItem = currentRole,
                    onItemSelected = { selectedRole = it },
                    summary = stringResource(currentRole.description),
                    itemIcon = { MeshtasticIcons.role(it) },
                    itemLabel = { it.name },
                )

                HorizontalDivider()

                val currentRebroadcastMode = formState.value.rebroadcast_mode
                DropDownPreference(
                    title = stringResource(Res.string.rebroadcast_mode),
                    enabled = state.connected,
                    selectedItem = currentRebroadcastMode,
                    onItemSelected = { formState.value = formState.value.copy(rebroadcast_mode = it) },
                    summary = stringResource(currentRebroadcastMode.description),
                )

                HorizontalDivider()

                val nodeInfoBroadcastIntervals = remember { IntervalConfiguration.NODE_INFO_BROADCAST.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.nodeinfo_broadcast_interval),
                    selectedItem = formState.value.node_info_broadcast_secs.toLong(),
                    enabled = state.connected,
                    items = nodeInfoBroadcastIntervals.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(node_info_broadcast_secs = it.toInt()) },
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.hardware)) {
                SwitchPreference(
                    title = stringResource(Res.string.double_tap_as_button_press),
                    summary = stringResource(Res.string.config_device_doubleTapAsButtonPress_summary),
                    checked = formState.value.double_tap_as_button_press,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(double_tap_as_button_press = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )

                InsetDivider()

                SwitchPreference(
                    title = stringResource(Res.string.triple_click_adhoc_ping),
                    summary = stringResource(Res.string.config_device_tripleClickAsAdHocPing_summary),
                    checked = !formState.value.disable_triple_click,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(disable_triple_click = !it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )

                InsetDivider()

                SwitchPreference(
                    title = stringResource(Res.string.led_heartbeat),
                    summary = stringResource(Res.string.config_device_ledHeartbeatEnabled_summary),
                    checked = !formState.value.led_heartbeat_disabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(led_heartbeat_disabled = !it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.time_zone)) {
                val appTzPosixString = rememberSystemTimeZonePosixString()

                EditTextPreference(
                    title = "",
                    value = formState.value.tzdef,
                    summary = stringResource(Res.string.config_device_tzdef_summary),
                    maxSize = 64, // tzdef max_size:65
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(tzdef = it) },
                    trailingIcon = {
                        IconButton(onClick = { formState.value = formState.value.copy(tzdef = "") }) {
                            Icon(
                                imageVector = MeshtasticIcons.Close,
                                contentDescription = stringResource(Res.string.clear_time_zone),
                            )
                        }
                    },
                )

                HorizontalDivider()

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.connected,
                    shape = RectangleShape,
                    onClick = { formState.value = formState.value.copy(tzdef = appTzPosixString) },
                ) {
                    Icon(
                        imageVector = MeshtasticIcons.PhoneAndroid,
                        contentDescription = stringResource(Res.string.config_device_use_phone_tz),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(text = stringResource(Res.string.config_device_use_phone_tz))
                }
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.gpio)) {
                EditTextPreference(
                    title = stringResource(Res.string.button_gpio),
                    value = formState.value.button_gpio,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(button_gpio = it) },
                )

                HorizontalDivider()

                EditTextPreference(
                    title = stringResource(Res.string.buzzer_gpio),
                    value = formState.value.buzzer_gpio,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(buzzer_gpio = it) },
                )
            }
        }

        if ((state.deviceUIConfig != null || state.fileManifest.isNotEmpty()) && isDebug) {
            item {
                TitledCard(title = stringResource(Res.string.device_storage_ui_title)) {
                    state.deviceUIConfig?.let { uiConfig ->
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            text =
                            stringResource(
                                Res.string.device_theme_language,
                                uiConfig.theme.toString(),
                                uiConfig.language.toString(),
                            ),
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (state.fileManifest.isNotEmpty()) {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            text = stringResource(Res.string.files_available, state.fileManifest.size),
                        )
                        state.fileManifest.forEach { file ->
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                text = stringResource(Res.string.file_entry, file.file_name, file.size_bytes),
                            )
                        }
                    } else {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            text = stringResource(Res.string.no_files_manifested),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RouterRoleConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val dialogTitle = stringResource(Res.string.are_you_sure)
    val annotatedDialogText =
        annotatedStringFromHtml(
            html = stringResource(Res.string.router_role_confirmation_text),
            linkStyles = TextLinkStyles(style = SpanStyle(color = Color.Blue)),
        )

    var confirmed by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        title = { Text(text = dialogTitle) },
        text = {
            Column {
                Text(text = annotatedDialogText)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(true) { confirmed = !confirmed },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(stringResource(Res.string.i_know_what_i_m_doing))
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmed) { Text(stringResource(Res.string.accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
