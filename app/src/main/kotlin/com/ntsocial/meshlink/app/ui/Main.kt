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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ntsocial.meshlink.app.radio.AndroidEndpointConversationSourceCoordinator
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.EndpointTarget
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.TopLevelDestination
import com.ntsocial.meshlink.core.navigation.rememberMultiBackstack
import com.ntsocial.meshlink.core.navigation.withoutEndpoint
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.ui.component.EndpointImportOption
import com.ntsocial.meshlink.core.ui.component.FleetImportDialogs
import com.ntsocial.meshlink.core.ui.component.MeshtasticAppShell
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavDisplay
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavigationSuite
import com.ntsocial.meshlink.core.ui.navigation.BindEndpointSelection
import com.ntsocial.meshlink.core.ui.navigation.endpointEntryProvider
import com.ntsocial.meshlink.core.ui.navigation.endpointTabsPadding
import com.ntsocial.meshlink.core.ui.util.rememberUiAction
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.feature.connections.navigation.connectionsGraph
import com.ntsocial.meshlink.feature.firmware.navigation.firmwareGraph
import com.ntsocial.meshlink.feature.meshcore.navigation.meshCoreGraph
import com.ntsocial.meshlink.feature.messaging.fleet.rememberFleetNavigationState
import com.ntsocial.meshlink.feature.messaging.navigation.FleetChannelsEntry
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import com.ntsocial.meshlink.feature.wifiprovision.navigation.wifiProvisionGraph
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val viewModel: UIViewModel = koinViewModel()
    val dialogFleet = koinInject<RadioFleetManager>()
    val dialogSnapshots by dialogFleet.snapshots.collectAsStateWithLifecycle()
    // Land on Connections for first-run / no-device-selected; otherwise on Nodes. Read synchronously
    // from the StateFlow (seeded from persisted prefs) so the initial tab is set in one shot.
    val initialTab =
        if (viewModel.currentDeviceAddressFlow.value.isNullOrSelectedNone()) {
            TopLevelDestination.Connections.route
        } else {
            NodesRoute.NodesGraph
        }
    val multiBackstack = rememberMultiBackstack(initialTab)

    MeshtasticAppShell(
        multiBackstack = multiBackstack,
        uiViewModel = viewModel,
        hostModifier = modifier,
        sharedDialogs = {
            FleetImportDialogs(
                viewModel = viewModel,
                options =
                dialogSnapshots.values
                    .filter { it.profile.enabled }
                    .map {
                        EndpointImportOption(
                            EndpointTarget(it.profile.id.value, it.runtimeId),
                            "${it.profile.displayName} · ${it.profile.addressSuffix.uppercase()}",
                            it.state is EndpointSessionState.Ready,
                        )
                    },
            ) { owner, content ->
                EndpointScopeHost(
                    RadioEndpointId(owner.endpointId),
                    expectedRuntimeId = owner.runtimeId,
                    content = content,
                )
            }
        },
    ) {
        MeshtasticNavigationSuite(
            multiBackstack = multiBackstack,
            uiViewModel = viewModel,
            radioState = rememberFleetNavigationState(),
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
    val conversationSourceCoordinator = koinInject<AndroidEndpointConversationSourceCoordinator>()
    val snapshots by fleetManager.snapshots.collectAsStateWithLifecycle()
    val selectedEndpointId by fleetManager.selectedEndpointId.collectAsStateWithLifecycle()
    val uiAction = rememberUiAction()
    LaunchedEffect(conversationSourceCoordinator) { conversationSourceCoordinator.start() }
    val scopeMode = featureScopeMode(multiBackstack)
    val endpointList =
        snapshots.values
            .filter { it.profile.enabled }
            .sortedWith(
                compareByDescending<RadioEndpointSnapshot> { it.profile.legacyPrimary }
                    .thenBy { it.profile.displayName },
            )
    val selectedSnapshot = selectedEndpointId?.let(snapshots::get)
    val target =
        selectedSnapshot?.takeIf { it.profile.enabled }?.let { EndpointTarget(it.profile.id.value, it.runtimeId) }
    BindEndpointSelection(multiBackstack, target)
    val navigationContent: @Composable () -> Unit = {
        RadioNavigationDisplay(
            multiBackstack = multiBackstack,
            backStack = multiBackstack.activeBackStack,
            viewModel = viewModel,
        )
    }

    val showEndpointTabs = scopeMode == FeatureScopeMode.SELECTED_ENDPOINT && endpointList.isNotEmpty()
    Column(modifier = Modifier.fillMaxSize().endpointTabsPadding(showEndpointTabs)) {
        if (showEndpointTabs) {
            RadioEndpointTabs(
                endpoints = endpointList,
                selectedEndpointId = selectedEndpointId,
                onSelect = { endpointId ->
                    if (endpointId != selectedEndpointId) {
                        uiAction { fleetManager.select(endpointId) }
                    }
                },
            )
        }
        Box(Modifier.weight(1f)) { navigationContent() }
    }
}

@Composable
private fun RadioNavigationDisplay(
    multiBackstack: MultiBackstack,
    backStack: NavBackStack<NavKey>,
    viewModel: UIViewModel,
) {
    val fleetManager = koinInject<RadioFleetManager>()
    val provider =
        entryProvider<NavKey> {
            contactsGraph(
                backStack = backStack,
                scrollToTopEvents = viewModel.scrollToTopEventFlow,
                fleetRootContent = { FleetChannelsEntry(multiBackstack, fleetManager) },
                endpointContent = { endpointId, expectedGeneration, content ->
                    EndpointScopeHost(
                        endpointId = RadioEndpointId(endpointId),
                        expectedGeneration = expectedGeneration,
                    ) {
                        content()
                    }
                },
            )
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
    MeshtasticNavDisplay(
        multiBackstack = multiBackstack,
        entryProvider =
        endpointEntryProvider(
            provider = provider,
            onNavigateToConnections = { multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route) },
        ) { target, content ->
            EndpointScopeHost(
                RadioEndpointId(target.endpointId),
                expectedRuntimeId = target.runtimeId,
                content = content,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
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
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(endpointTabStateColor(endpoint.state)))
                        Spacer(Modifier.width(7.dp))
                        Text("${endpoint.profile.displayName} · ${endpoint.profile.addressSuffix.uppercase()}")
                    }
                },
            )
        }
    }
}

private enum class FeatureScopeMode {
    ROOT,
    SELECTED_ENDPOINT,
    FLEET,
}

private fun featureScopeMode(multiBackstack: MultiBackstack): FeatureScopeMode {
    val currentTopLevel = multiBackstack.currentTabRoute
    return if (
        (currentTopLevel == TopLevelDestination.Connections.route && multiBackstack.activeBackStack.size == 1) ||
        currentTopLevel == TopLevelDestination.MeshCore.route
    ) {
        FeatureScopeMode.ROOT
    } else if (currentTopLevel != TopLevelDestination.Conversations.route) {
        FeatureScopeMode.SELECTED_ENDPOINT
    } else {
        when (multiBackstack.activeBackStack.lastOrNull()?.withoutEndpoint()) {
            ContactsRoute.ContactsGraph,
            ContactsRoute.Contacts,
            -> FeatureScopeMode.FLEET

            else -> FeatureScopeMode.SELECTED_ENDPOINT
        }
    }
}

@Composable
private fun endpointTabStateColor(state: EndpointSessionState): Color = when (state) {
    is EndpointSessionState.Ready -> Color(ENDPOINT_READY_COLOR)

    EndpointSessionState.Connecting,
    EndpointSessionState.Synchronizing,
    -> Color(ENDPOINT_CONNECTING_COLOR)

    is EndpointSessionState.Degraded,
    is EndpointSessionState.Failed,
    -> Color(ENDPOINT_ATTENTION_COLOR)

    EndpointSessionState.Releasing,
    EndpointSessionState.Registered,
    EndpointSessionState.WaitingResource,
    -> MaterialTheme.colorScheme.outline
}

private const val ENDPOINT_READY_COLOR = 0xff16a34a
private const val ENDPOINT_CONNECTING_COLOR = 0xff2563eb
private const val ENDPOINT_ATTENTION_COLOR = 0xffd97706

/** True when no device address is persisted, or the address is the "none" sentinel (`"n"`). */
private fun String?.isNullOrSelectedNone(): Boolean = isNullOrBlank() || this == "n"
