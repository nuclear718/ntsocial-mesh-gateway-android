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

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.getColorFrom
import com.ntsocial.meshlink.core.model.getStringResFrom
import com.ntsocial.meshlink.core.repository.TakPrefs
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.export_tak_data_package
import com.ntsocial.meshlink.core.resources.tak
import com.ntsocial.meshlink.core.resources.tak_config
import com.ntsocial.meshlink.core.resources.tak_role
import com.ntsocial.meshlink.core.resources.tak_server_enabled
import com.ntsocial.meshlink.core.resources.tak_server_enabled_desc
import com.ntsocial.meshlink.core.resources.tak_team
import com.ntsocial.meshlink.core.takserver.TAKDataPackageGenerator
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Share
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.tak.TakPermissionHandler
import com.ntsocial.meshlink.feature.settings.tak.rememberDataPackageExporter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.proto.ModuleConfig

@Composable
fun TAKConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val takConfig = state.moduleConfig.tak ?: ModuleConfig.TAKConfig()
    val formState = rememberConfigState(initialValue = takConfig)

    val takPrefs: TakPrefs = koinInject()
    val isTakServerEnabled by takPrefs.isTakServerEnabled.collectAsStateWithLifecycle()

    val exportLauncher = rememberDataPackageExporter { TAKDataPackageGenerator.generateDataPackage() }

    LaunchedEffect(takConfig) { formState.value = takConfig }

    TakPermissionHandler(
        isTakServerEnabled = isTakServerEnabled,
        onPermissionResult = { granted ->
            if (!granted && isTakServerEnabled) {
                takPrefs.setTakServerEnabled(false)
            }
        },
    )

    RadioConfigScreenList(
        title = stringResource(Res.string.tak),
        onBack = onBack,
        actions = {
            IconButton(onClick = { exportLauncher("Meshtastic_TAK_Server.zip") }) {
                Icon(
                    imageVector = MeshtasticIcons.Share,
                    contentDescription = stringResource(Res.string.export_tak_data_package),
                )
            }
        },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(tak = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TAKConfigCard(
                formState = formState,
                isTakServerEnabled = isTakServerEnabled,
                isConnected = state.connected,
                onTakServerEnabledChange = { takPrefs.setTakServerEnabled(it) },
            )
        }
    }
}

@Composable
private fun TAKConfigCard(
    formState: ConfigState<ModuleConfig.TAKConfig>,
    isTakServerEnabled: Boolean,
    isConnected: Boolean,
    onTakServerEnabledChange: (Boolean) -> Unit,
) {
    TitledCard(title = stringResource(Res.string.tak_config)) {
        SwitchPreference(
            title = stringResource(Res.string.tak_server_enabled),
            summary = stringResource(Res.string.tak_server_enabled_desc),
            checked = isTakServerEnabled,
            enabled = true,
            onCheckedChange = onTakServerEnabledChange,
        )
        HorizontalDivider()
        DropDownPreference(
            title = stringResource(Res.string.tak_team),
            enabled = isConnected,
            selectedItem = formState.value.team,
            itemLabel = { stringResource(getStringResFrom(it)) },
            itemColor = { Color(getColorFrom(it)) },
            onItemSelected = { formState.value = formState.value.copy(team = it) },
        )
        HorizontalDivider()
        DropDownPreference(
            title = stringResource(Res.string.tak_role),
            enabled = isConnected,
            selectedItem = formState.value.role,
            itemLabel = { stringResource(getStringResFrom(it)) },
            onItemSelected = { formState.value = formState.value.copy(role = it) },
        )
    }
}
