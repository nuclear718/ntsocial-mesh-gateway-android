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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.ui.viewmodel.scopedViewModel
import com.ntsocial.meshlink.feature.settings.AboutScreen
import com.ntsocial.meshlink.feature.settings.AdministrationScreen
import com.ntsocial.meshlink.feature.settings.DeviceConfigurationScreen
import com.ntsocial.meshlink.feature.settings.DeviceLinkDirectoryScreen
import com.ntsocial.meshlink.feature.settings.DeviceLinkDirectoryViewModel
import com.ntsocial.meshlink.feature.settings.ModuleConfigurationScreen
import com.ntsocial.meshlink.feature.settings.SettingsViewModel
import com.ntsocial.meshlink.feature.settings.debugging.DebugScreen
import com.ntsocial.meshlink.feature.settings.debugging.DebugViewModel
import com.ntsocial.meshlink.feature.settings.filter.FilterSettingsScreen
import com.ntsocial.meshlink.feature.settings.filter.FilterSettingsViewModel
import com.ntsocial.meshlink.feature.settings.radio.CleanNodeDatabaseScreen
import com.ntsocial.meshlink.feature.settings.radio.CleanNodeDatabaseViewModel
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.radio.channel.ChannelConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.AmbientLightingConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.AudioConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.BluetoothConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.CannedMessageConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.DetectionSensorConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.DeviceConfigScreenCommon
import com.ntsocial.meshlink.feature.settings.radio.component.DisplayConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.ExternalNotificationConfigScreenCommon
import com.ntsocial.meshlink.feature.settings.radio.component.LoRaConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.MQTTConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.NeighborInfoConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.NetworkConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.PaxcounterConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.PositionConfigScreenCommon
import com.ntsocial.meshlink.feature.settings.radio.component.PowerConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.RangeTestConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.RemoteHardwareConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.SecurityConfigScreenCommon
import com.ntsocial.meshlink.feature.settings.radio.component.SerialConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.StatusMessageConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.StoreForwardConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.TAKConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.TelemetryConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.TrafficManagementConfigScreen
import com.ntsocial.meshlink.feature.settings.radio.component.UserConfigScreen
import kotlin.reflect.KClass

@Composable
fun getRadioConfigViewModel(backStack: NavBackStack<NavKey>): RadioConfigViewModel {
    val viewModel = scopedViewModel<RadioConfigViewModel>()
    val destNum =
        remember(backStack.toList()) {
            backStack.lastOrNull { it is SettingsRoute.Settings }?.let { (it as SettingsRoute.Settings).destNum }
                ?: backStack
                    .lastOrNull { it is SettingsRoute.SettingsGraph }
                    ?.let { (it as SettingsRoute.SettingsGraph).destNum }
        }
    LaunchedEffect(destNum) { viewModel.initDestNum(destNum) }
    return viewModel
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun EntryProviderScope<NavKey>.settingsGraph(backStack: NavBackStack<NavKey>) {
    entry<SettingsRoute.SettingsGraph> {
        SettingsMainScreen(
            settingsViewModel = scopedViewModel(),
            radioConfigViewModel = getRadioConfigViewModel(backStack),
            onClickNodeChip = { backStack.add(NodesRoute.NodeDetail(it)) },
            onNavigate = { backStack.add(it) },
        )
    }

    entry<SettingsRoute.Settings> {
        SettingsMainScreen(
            settingsViewModel = scopedViewModel(),
            radioConfigViewModel = getRadioConfigViewModel(backStack),
            onClickNodeChip = { backStack.add(NodesRoute.NodeDetail(it)) },
            onNavigate = { backStack.add(it) },
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoute.DeviceConfiguration> {
        DeviceConfigurationScreen(
            viewModel = getRadioConfigViewModel(backStack),
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigate = { route -> backStack.add(route) },
        )
    }

    entry<SettingsRoute.ModuleConfiguration> {
        val settingsViewModel: SettingsViewModel = scopedViewModel()
        val excludedModulesUnlocked by settingsViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
        ModuleConfigurationScreen(
            viewModel = getRadioConfigViewModel(backStack),
            excludedModulesUnlocked = excludedModulesUnlocked,
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigate = { route -> backStack.add(route) },
        )
    }

    entry<SettingsRoute.Administration> {
        AdministrationScreen(
            viewModel = getRadioConfigViewModel(backStack),
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoute.CleanNodeDb> {
        val viewModel: CleanNodeDatabaseViewModel = scopedViewModel()
        CleanNodeDatabaseScreen(viewModel = viewModel)
    }

    ConfigRoute.entries.forEach { routeInfo ->
        configComposable(routeInfo.route::class, backStack) { viewModel ->
            LaunchedEffect(Unit) { viewModel.setResponseStateLoading(routeInfo) }
            when (routeInfo) {
                ConfigRoute.USER ->
                    UserConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.CHANNELS ->
                    ChannelConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.DEVICE ->
                    DeviceConfigScreenCommon(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.POSITION ->
                    PositionConfigScreenCommon(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.POWER ->
                    PowerConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.NETWORK ->
                    NetworkConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.DISPLAY ->
                    DisplayConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.LORA ->
                    LoRaConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.BLUETOOTH ->
                    BluetoothConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.SECURITY ->
                    SecurityConfigScreenCommon(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
            }
        }
    }

    ModuleRoute.entries.forEach { routeInfo ->
        configComposable(routeInfo.route::class, backStack) { viewModel ->
            LaunchedEffect(Unit) { viewModel.setResponseStateLoading(routeInfo) }
            when (routeInfo) {
                ModuleRoute.MQTT ->
                    MQTTConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.SERIAL ->
                    SerialConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.EXT_NOTIFICATION ->
                    ExternalNotificationConfigScreenCommon(
                        viewModel = viewModel,
                        onBack = dropUnlessResumed { backStack.removeLastOrNull() },
                    )

                ModuleRoute.STORE_FORWARD ->
                    StoreForwardConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.RANGE_TEST ->
                    RangeTestConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.TELEMETRY ->
                    TelemetryConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.CANNED_MESSAGE ->
                    CannedMessageConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.AUDIO ->
                    AudioConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.REMOTE_HARDWARE ->
                    RemoteHardwareConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.NEIGHBOR_INFO ->
                    NeighborInfoConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.AMBIENT_LIGHTING ->
                    AmbientLightingConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.DETECTION_SENSOR ->
                    DetectionSensorConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.PAXCOUNTER ->
                    PaxcounterConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.STATUS_MESSAGE ->
                    StatusMessageConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.TRAFFIC_MANAGEMENT ->
                    TrafficManagementConfigScreen(
                        viewModel,
                        onBack = dropUnlessResumed { backStack.removeLastOrNull() },
                    )

                ModuleRoute.TAK ->
                    TAKConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
            }
        }
    }

    entry<SettingsRoute.DebugPanel> {
        val viewModel: DebugViewModel = scopedViewModel()
        DebugScreen(viewModel = viewModel, onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }

    entry<SettingsRoute.DeviceLinks> {
        val viewModel: DeviceLinkDirectoryViewModel = scopedViewModel()
        DeviceLinkDirectoryScreen(
            viewModel = viewModel,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoute.About> {
        AboutScreen(
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            jsonProvider = { getAboutLibrariesJson() },
        )
    }

    entry<SettingsRoute.FilterSettings> {
        val viewModel: FilterSettingsViewModel = scopedViewModel()
        FilterSettingsScreen(viewModel = viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
    }
}

/** Expect declaration for the platform-specific settings main screen. */
@Composable
expect fun SettingsMainScreen(
    settingsViewModel: SettingsViewModel,
    radioConfigViewModel: RadioConfigViewModel,
    onClickNodeChip: (Int) -> Unit,
    onNavigate: (Route) -> Unit,
    onBack: (() -> Unit)? = null,
)

/** Expect declarations for platform-specific config screens. */
fun <R : Route> EntryProviderScope<NavKey>.configComposable(
    route: KClass<R>,
    backStack: NavBackStack<NavKey>,
    content: @Composable (RadioConfigViewModel) -> Unit,
) {
    addEntryProvider(route) { content(getRadioConfigViewModel(backStack)) }
}
