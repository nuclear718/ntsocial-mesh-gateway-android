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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.bluetooth
import com.ntsocial.meshlink.core.resources.bluetooth_config
import com.ntsocial.meshlink.core.resources.bluetooth_enabled
import com.ntsocial.meshlink.core.resources.fixed_pin
import com.ntsocial.meshlink.core.resources.pairing_mode
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.EditTextPreference
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@Composable
fun BluetoothConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val bluetoothConfig = state.radioConfig.bluetooth ?: Config.BluetoothConfig()
    val formState = rememberConfigState(initialValue = bluetoothConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.bluetooth),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config(bluetooth = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.bluetooth_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.bluetooth_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.pairing_mode),
                    enabled = state.connected,
                    items =
                    Config.BluetoothConfig.PairingMode.entries
                        .filter { it.name != "UNRECOGNIZED" }
                        .map { it to it.name },
                    selectedItem =
                    formState.value.mode.takeUnless { it.name == "UNRECOGNIZED" }
                        ?: Config.BluetoothConfig.PairingMode.RANDOM_PIN,
                    onItemSelected = { formState.value = formState.value.copy(mode = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.fixed_pin),
                    value = formState.value.fixed_pin,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        if (it.toString().length == 6) { // ensure 6 digits
                            formState.value = formState.value.copy(fixed_pin = it)
                        }
                    },
                )
            }
        }
    }
}
