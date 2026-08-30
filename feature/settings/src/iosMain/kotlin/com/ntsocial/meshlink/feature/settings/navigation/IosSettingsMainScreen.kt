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
package com.ntsocial.meshlink.feature.settings.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.database.DatabaseConstants
import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.acknowledgements
import com.ntsocial.meshlink.core.resources.app_language
import com.ntsocial.meshlink.core.resources.app_settings
import com.ntsocial.meshlink.core.resources.app_version
import com.ntsocial.meshlink.core.resources.bottom_nav_settings
import com.ntsocial.meshlink.core.resources.device_db_cache_limit
import com.ntsocial.meshlink.core.resources.device_db_cache_limit_summary
import com.ntsocial.meshlink.core.resources.info
import com.ntsocial.meshlink.core.resources.language_english
import com.ntsocial.meshlink.core.resources.language_japanese
import com.ntsocial.meshlink.core.resources.language_traditional_chinese
import com.ntsocial.meshlink.core.resources.remotely_administrating
import com.ntsocial.meshlink.core.resources.theme
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.component.MainAppBar
import com.ntsocial.meshlink.core.ui.component.MeshtasticDialog
import com.ntsocial.meshlink.core.ui.icon.ChevronRight
import com.ntsocial.meshlink.core.ui.icon.FormatPaint
import com.ntsocial.meshlink.core.ui.icon.Info
import com.ntsocial.meshlink.core.ui.icon.Language
import com.ntsocial.meshlink.core.ui.icon.Memory
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.feature.settings.LocalPlatformSettingsSection
import com.ntsocial.meshlink.feature.settings.SettingsViewModel
import com.ntsocial.meshlink.feature.settings.component.ExpressiveSection
import com.ntsocial.meshlink.feature.settings.component.HomoglyphSetting
import com.ntsocial.meshlink.feature.settings.component.ThemePickerDialog
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigItemList
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
@Suppress("LongMethod")
internal fun IosSettingsMainScreen(
    settingsViewModel: SettingsViewModel,
    radioConfigViewModel: RadioConfigViewModel,
    onClickNodeChip: (Int) -> Unit,
    onNavigate: (Route) -> Unit,
    onBack: (() -> Unit)?,
) {
    val state by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by radioConfigViewModel.destNode.collectAsStateWithLifecycle()
    val localConfig by settingsViewModel.localConfig.collectAsStateWithLifecycle()
    val homoglyphEnabled by radioConfigViewModel.homoglyphEncodingEnabledFlow.collectAsStateWithLifecycle(false)
    val cacheLimit by settingsViewModel.dbCacheLimit.collectAsStateWithLifecycle()
    val currentLocale by settingsViewModel.locale.collectAsStateWithLifecycle()
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showLanguagePickerDialog by remember { mutableStateOf(false) }

    if (showThemePickerDialog) {
        ThemePickerDialog(onClickTheme = settingsViewModel::setTheme, onDismiss = { showThemePickerDialog = false })
    }
    if (showLanguagePickerDialog) {
        IosLanguagePickerDialog(
            currentTag = currentLocale,
            onDismiss = { showLanguagePickerDialog = false },
            onSelect = settingsViewModel::setLocale,
        )
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.bottom_nav_settings),
                subtitle =
                if (state.isLocal) {
                    null
                } else {
                    stringResource(Res.string.remotely_administrating, destNode?.user?.long_name.orEmpty())
                },
                ourNode = destNode,
                showNodeChip = !state.isLocal,
                canNavigateUp = onBack != null,
                onNavigateUp = onBack ?: {},
                actions = {},
                onClickChip = { destNode?.num?.let(onClickNodeChip) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
            Modifier.padding(paddingValues).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioConfigItemList(
                state = state,
                isManaged = localConfig.security?.is_managed ?: false,
                isOtaCapable = false,
                showBackupRestore = false,
                onRouteClick = { route ->
                    when (route) {
                        is ConfigRoute -> onNavigate(route.route)
                        is ModuleRoute -> onNavigate(route.route)
                    }
                },
                onNavigate = onNavigate,
                onImport = {},
                onExport = {},
            )

            if (state.isLocal) {
                ExpressiveSection(title = stringResource(Res.string.app_settings)) {
                    ListItem(
                        text = stringResource(Res.string.app_language),
                        leadingIcon = MeshtasticIcons.Language,
                        trailingIcon = MeshtasticIcons.ChevronRight,
                        onClick = { showLanguagePickerDialog = true },
                    )
                    ListItem(
                        text = stringResource(Res.string.theme),
                        leadingIcon = MeshtasticIcons.FormatPaint,
                        trailingIcon = null,
                        onClick = { showThemePickerDialog = true },
                    )
                    HomoglyphSetting(
                        homoglyphEncodingEnabled = homoglyphEnabled,
                        onToggle = radioConfigViewModel::toggleHomoglyphCharactersEncodingEnabled,
                    )
                    val cacheItems = remember {
                        (DatabaseConstants.MIN_CACHE_LIMIT..DatabaseConstants.MAX_CACHE_LIMIT).map { limit ->
                            limit.toLong() to limit.toString()
                        }
                    }
                    DropDownPreference(
                        title = stringResource(Res.string.device_db_cache_limit),
                        enabled = true,
                        items = cacheItems,
                        selectedItem = cacheLimit.toLong(),
                        onItemSelected = { selected -> settingsViewModel.setDbCacheLimit(selected.toInt()) },
                        summary = stringResource(Res.string.device_db_cache_limit_summary),
                    )
                }

                LocalPlatformSettingsSection.current?.invoke()

                ExpressiveSection(title = stringResource(Res.string.info)) {
                    ListItem(
                        text = stringResource(Res.string.acknowledgements),
                        leadingIcon = MeshtasticIcons.Info,
                        trailingIcon = MeshtasticIcons.ChevronRight,
                        onClick = { onNavigate(SettingsRoute.About) },
                    )
                    ListItem(
                        text = stringResource(Res.string.app_version),
                        leadingIcon = MeshtasticIcons.Memory,
                        supportingText = settingsViewModel.appVersionName,
                        trailingIcon = null,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun IosLanguagePickerDialog(currentTag: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val languages =
        listOf(
            "en" to stringResource(Res.string.language_english),
            "zh-TW" to stringResource(Res.string.language_traditional_chinese),
            "ja" to stringResource(Res.string.language_japanese),
        )
    MeshtasticDialog(
        title = stringResource(Res.string.app_language),
        onDismiss = onDismiss,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                languages.forEach { (languageTag, languageName) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = currentTag.equals(languageTag, ignoreCase = true),
                                onClick = {
                                    onSelect(languageTag)
                                    onDismiss()
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = currentTag.equals(languageTag, ignoreCase = true), onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = languageName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}
