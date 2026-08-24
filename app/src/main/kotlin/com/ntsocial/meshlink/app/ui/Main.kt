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
@file:Suppress("MatchingDeclarationName")

package com.ntsocial.meshlink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ntsocial.meshlink.app.radio.RadioEndpointScopeRegistry
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.TopLevelDestination
import com.ntsocial.meshlink.core.navigation.rememberMultiBackstack
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.radio_endpoint_switching
import com.ntsocial.meshlink.core.ui.component.MeshtasticAppShell
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavDisplay
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavigationSuite
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.feature.connections.navigation.connectionsGraph
import com.ntsocial.meshlink.feature.firmware.navigation.firmwareGraph
import com.ntsocial.meshlink.feature.meshcore.navigation.meshCoreGraph
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import com.ntsocial.meshlink.feature.wifiprovision.navigation.wifiProvisionGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.scope.UnboundKoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.scope.Scope

@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
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

    MeshtasticAppShell(multiBackstack = multiBackstack, uiViewModel = viewModel, hostModifier = modifier) {
        MeshtasticNavigationSuite(
            multiBackstack = multiBackstack,
            uiViewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        ) {
            EndpointAwareNavigation(multiBackstack = multiBackstack)
        }
    }
}

@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
@Composable
private fun EndpointAwareNavigation(multiBackstack: MultiBackstack) {
    val viewModel: UIViewModel = koinViewModel()
    val fleetManager = koinInject<RadioFleetManager>()
    val scopeRegistry = koinInject<RadioEndpointScopeRegistry>()
    val snapshots by fleetManager.snapshots.collectAsStateWithLifecycle()
    val selectedEndpointId by fleetManager.selectedEndpointId.collectAsStateWithLifecycle()
    val endpointScopes by scopeRegistry.scopes.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val endpointAware =
        multiBackstack.currentTabRoute != TopLevelDestination.Connections.route &&
            multiBackstack.currentTabRoute != TopLevelDestination.MeshCore.route
    val endpointList =
        snapshots.values.sortedWith(
            compareByDescending<RadioEndpointSnapshot> { it.profile.legacyPrimary }.thenBy { it.profile.displayName },
        )
    val selectedSnapshot = selectedEndpointId?.let(snapshots::get)
    val navigationContent: @Composable () -> Unit = {
        RadioNavigationDisplay(
            multiBackstack = multiBackstack,
            backStack = multiBackstack.activeBackStack,
            viewModel = viewModel,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (endpointAware && endpointList.isNotEmpty()) {
            RadioEndpointTabs(
                endpoints = endpointList,
                selectedEndpointId = selectedEndpointId,
                onSelect = { endpointId ->
                    if (endpointId != selectedEndpointId) {
                        coroutineScope.launch {
                            fleetManager.select(endpointId)
                            // A nested route belongs to the previous radio. Reset before entering another scope.
                            multiBackstack.navigateTopLevel(multiBackstack.currentTabRoute)
                        }
                    }
                },
            )
        }
        ScopedRadioNavigation(
            endpointAware = endpointAware,
            selectedSnapshot = selectedSnapshot,
            endpointScope = selectedSnapshot?.let { endpointScopes[it.profile.id] },
            navigationContent = navigationContent,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
@Composable
private fun ScopedRadioNavigation(
    endpointAware: Boolean,
    selectedSnapshot: RadioEndpointSnapshot?,
    endpointScope: Scope?,
    navigationContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movableNavigationContent = remember(navigationContent) { movableContentOf { navigationContent() } }
    when {
        !endpointAware || selectedSnapshot == null || selectedSnapshot.profile.legacyPrimary ->
            Box(modifier = modifier) { movableNavigationContent() }

        endpointScope != null ->
            Box(modifier = modifier) { UnboundKoinScope(scope = endpointScope) { movableNavigationContent() } }

        else ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(Res.string.radio_endpoint_switching),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
    }
}

@Composable
private fun RadioNavigationDisplay(
    multiBackstack: MultiBackstack,
    backStack: NavBackStack<NavKey>,
    viewModel: UIViewModel,
) {
    val provider =
        entryProvider<NavKey> {
            contactsGraph(backStack, viewModel.scrollToTopEventFlow)
            nodesGraph(
                backStack = backStack,
                scrollToTopEvents = viewModel.scrollToTopEventFlow,
                onHandleDeepLink = viewModel::handleDeepLink,
                onNavigateToConnections = { multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route) },
            )
            meshCoreGraph(backStack)
            channelsGraph(backStack)
            connectionsGraph(backStack)
            settingsGraph(backStack)
            firmwareGraph(backStack)
            wifiProvisionGraph(backStack)
        }
    MeshtasticNavDisplay(multiBackstack = multiBackstack, entryProvider = provider, modifier = Modifier.fillMaxSize())
}

@Composable
private fun RadioEndpointTabs(
    endpoints: List<RadioEndpointSnapshot>,
    selectedEndpointId: RadioEndpointId?,
    onSelect: (RadioEndpointId) -> Unit,
) {
    val selectedIndex = endpoints.indexOfFirst { it.profile.id == selectedEndpointId }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        endpoints.forEach { endpoint ->
            Tab(
                selected = endpoint.profile.id == selectedEndpointId,
                onClick = { onSelect(endpoint.profile.id) },
                text = { Text(endpoint.profile.addressSuffix.uppercase()) },
            )
        }
    }
}

/** True when no device address is persisted, or the address is the "none" sentinel (`"n"`). */
private fun String?.isNullOrSelectedNone(): Boolean = isNullOrBlank() || this == "n"
