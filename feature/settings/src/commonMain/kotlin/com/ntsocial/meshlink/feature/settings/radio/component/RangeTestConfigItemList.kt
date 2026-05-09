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
package com.ntsocial.meshlink.feature.settings.radio.component

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.range_test
import com.ntsocial.meshlink.core.resources.range_test_config
import com.ntsocial.meshlink.core.resources.range_test_enabled
import com.ntsocial.meshlink.core.resources.save_csv_in_storage_esp32_only
import com.ntsocial.meshlink.core.resources.sender_message_interval_seconds
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.util.IntervalConfiguration
import com.ntsocial.meshlink.feature.settings.util.toDisplayString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.ModuleConfig

@Composable
fun RangeTestConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val rangeTestConfig = state.moduleConfig.range_test ?: ModuleConfig.RangeTestConfig()
    val formState = rememberConfigState(initialValue = rangeTestConfig)

    RadioConfigScreenList(
        title = stringResource(Res.string.range_test),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(range_test = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.range_test_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.range_test_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val rangeItems = remember { IntervalConfiguration.RANGE_TEST_SENDER.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.sender_message_interval_seconds),
                    selectedItem = (formState.value.sender).toLong(),
                    enabled = state.connected,
                    items = rangeItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy(sender = it.toInt()) },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.save_csv_in_storage_esp32_only),
                    checked = formState.value.save,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(save = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
