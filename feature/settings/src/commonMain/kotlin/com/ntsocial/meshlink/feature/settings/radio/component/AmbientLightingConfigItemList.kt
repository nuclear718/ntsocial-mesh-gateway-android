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
import com.ntsocial.meshlink.core.resources.ambient_lighting
import com.ntsocial.meshlink.core.resources.ambient_lighting_config
import com.ntsocial.meshlink.core.resources.blue
import com.ntsocial.meshlink.core.resources.current
import com.ntsocial.meshlink.core.resources.green
import com.ntsocial.meshlink.core.resources.led_state
import com.ntsocial.meshlink.core.resources.red
import com.ntsocial.meshlink.core.ui.component.EditTextPreference
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.ModuleConfig

@Composable
fun AmbientLightingConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val ambientLightingConfig = state.moduleConfig.ambient_lighting ?: ModuleConfig.AmbientLightingConfig()
    val formState = rememberConfigState(initialValue = ambientLightingConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.ambient_lighting),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(ambient_lighting = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.ambient_lighting_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.led_state),
                    checked = formState.value.led_state,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(led_state = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.current),
                    value = formState.value.current,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(current = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.red),
                    value = formState.value.red,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(red = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.green),
                    value = formState.value.green,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(green = it) },
                )

                EditTextPreference(
                    title = stringResource(Res.string.blue),
                    value = formState.value.blue,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(blue = it) },
                )
            }
        }
    }
}
