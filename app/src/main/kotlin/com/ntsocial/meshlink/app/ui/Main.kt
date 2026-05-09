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
@file:Suppress("MatchingDeclarationName")

package com.ntsocial.meshlink.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.app.BuildConfig
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.TopLevelDestination
import com.ntsocial.meshlink.core.navigation.rememberMultiBackstack
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.app_too_old
import com.ntsocial.meshlink.core.resources.must_update
import com.ntsocial.meshlink.core.ui.component.MeshtasticAppShell
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavDisplay
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavigationSuite
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.feature.connections.navigation.connectionsGraph
import com.ntsocial.meshlink.feature.firmware.navigation.firmwareGraph
import com.ntsocial.meshlink.feature.map.navigation.mapGraph
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import com.ntsocial.meshlink.feature.wifiprovision.navigation.wifiProvisionGraph
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen() {
    val viewModel: UIViewModel = koinViewModel()
    // Land on Connections for first-run / no-device-selected; otherwise on Nodes. Read synchronously
    // from the StateFlow (seeded from persisted prefs) so the initial tab is set in one shot.
    val initialTab =
        if (viewModel.currentDeviceAddressFlow.value.isNullOrSelectedNone()) {
            TopLevelDestination.Connections.route
        } else {
            NodesRoute.NodesGraph
        }
    val multiBackstack = rememberMultiBackstack(initialTab)
    val backStack = multiBackstack.activeBackStack

    AndroidAppVersionCheck(viewModel)

    MeshtasticAppShell(multiBackstack = multiBackstack, uiViewModel = viewModel, hostModifier = Modifier) {
        MeshtasticNavigationSuite(
            multiBackstack = multiBackstack,
            uiViewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        ) {
            val provider =
                entryProvider<NavKey> {
                    contactsGraph(backStack, viewModel.scrollToTopEventFlow)
                    nodesGraph(
                        backStack = backStack,
                        scrollToTopEvents = viewModel.scrollToTopEventFlow,
                        onHandleDeepLink = viewModel::handleDeepLink,
                        onNavigateToConnections = {
                            multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route)
                        },
                    )
                    mapGraph(backStack)
                    channelsGraph(backStack)
                    connectionsGraph(backStack)
                    settingsGraph(backStack)
                    firmwareGraph(backStack)
                    wifiProvisionGraph(backStack)
                }
            MeshtasticNavDisplay(
                multiBackstack = multiBackstack,
                entryProvider = provider,
                modifier = Modifier.fillMaxSize().recalculateWindowInsets().safeDrawingPadding(),
            )
        }
    }
}

/** True when no device address is persisted, or the address is the "none" sentinel (`"n"`). */
private fun String?.isNullOrSelectedNone(): Boolean = isNullOrBlank() || this == "n"

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun AndroidAppVersionCheck(viewModel: UIViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()

    LaunchedEffect(connectionState, myNodeInfo) {
        if (connectionState == ConnectionState.Connected) {
            myNodeInfo?.let { info ->
                val isOld = info.minAppVersion > BuildConfig.VERSION_CODE && BuildConfig.DEBUG.not()
                Logger.d {
                    "[FW_CHECK] App version check - minAppVersion: ${info.minAppVersion}, " +
                        "currentVersion: ${BuildConfig.VERSION_CODE}, isOld: $isOld"
                }

                if (isOld) {
                    Logger.w { "[FW_CHECK] App too old - showing update prompt" }
                    viewModel.showAlert(
                        titleRes = Res.string.app_too_old,
                        messageRes = Res.string.must_update,
                        onConfirm = { viewModel.setDeviceAddress("n") },
                    )
                }
            }
        }
    }
}
