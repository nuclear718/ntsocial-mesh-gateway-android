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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.adc_multiplier_override
import com.ntsocial.meshlink.core.resources.adc_multiplier_override_ratio
import com.ntsocial.meshlink.core.resources.battery_ina_2xx_i2c_address
import com.ntsocial.meshlink.core.resources.config_power_is_power_saving_summary
import com.ntsocial.meshlink.core.resources.enable_power_saving_mode
import com.ntsocial.meshlink.core.resources.minimum_wake_time_seconds
import com.ntsocial.meshlink.core.resources.power
import com.ntsocial.meshlink.core.resources.power_config
import com.ntsocial.meshlink.core.resources.shutdown_on_power_loss
import com.ntsocial.meshlink.core.resources.super_deep_sleep_duration_seconds
import com.ntsocial.meshlink.core.resources.wait_for_bluetooth_duration_seconds
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.EditTextPreference
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.util.IntervalConfiguration
import com.ntsocial.meshlink.feature.settings.util.toDisplayString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@Composable
fun PowerConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val powerConfig = state.radioConfig.power ?: Config.PowerConfig()
    val formState = rememberConfigState(initialValue = powerConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.power),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config(power = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.power_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.enable_power_saving_mode),
                    summary = stringResource(Res.string.config_power_is_power_saving_summary),
                    checked = formState.value.is_power_saving,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(is_power_saving = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val items = remember { IntervalConfiguration.ALL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.shutdown_on_power_loss),
                    selectedItem = formState.value.on_battery_shutdown_after_secs.toLong(),
                    enabled = state.connected,
                    items = items.map { it.value to it.toDisplayString() },
                    onItemSelected = {
                        formState.value = formState.value.copy(on_battery_shutdown_after_secs = it.toInt())
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.adc_multiplier_override),
                    checked = formState.value.adc_multiplier_override > 0f,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.copy(adc_multiplier_override = if (it) 1.0f else 0.0f)
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (formState.value.adc_multiplier_override > 0f) {
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.adc_multiplier_override_ratio),
                        value = formState.value.adc_multiplier_override,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy(adc_multiplier_override = it) },
                    )
                }
                HorizontalDivider()
                val waitBluetoothItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.wait_for_bluetooth_duration_seconds),
                    selectedItem = formState.value.wait_bluetooth_secs.toLong(),
                    enabled = state.connected,
                    items = waitBluetoothItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(wait_bluetooth_secs = it.toInt()) },
                )
                HorizontalDivider()
                val sdsSecsItems = remember { IntervalConfiguration.ALL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.super_deep_sleep_duration_seconds),
                    selectedItem = formState.value.sds_secs.toLong(),
                    onItemSelected = { formState.value = formState.value.copy(sds_secs = it.toInt()) },
                    enabled = state.connected,
                    items = sdsSecsItems.map { it.value to it.toDisplayString() },
                )
                HorizontalDivider()
                val minWakeItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.minimum_wake_time_seconds),
                    selectedItem = formState.value.min_wake_secs.toLong(),
                    enabled = state.connected,
                    items = minWakeItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(min_wake_secs = it.toInt()) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.battery_ina_2xx_i2c_address),
                    value = formState.value.device_battery_ina_address,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(device_battery_ina_address = it) },
                )
            }
        }
    }
}
