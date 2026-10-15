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

package com.ntsocial.meshlink.ios.runtime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ntsocial.meshlink.core.common.util.CommonUri
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
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_hub_endpoint_unavailable
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_butterfly
import com.ntsocial.meshlink.core.resources.radio_endpoint_switching
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
import com.ntsocial.meshlink.feature.meshcore.navigation.meshCoreGraph
import com.ntsocial.meshlink.feature.messaging.fleet.rememberFleetNavigationState
import com.ntsocial.meshlink.feature.messaging.navigation.FleetChannelsEntry
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.LocalPlatformSettingsSection
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import com.ntsocial.meshlink.feature.wifiprovision.navigation.wifiProvisionGraph
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.scope.UnboundKoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI

/** Full iOS product shell assembled from the same shared feature graphs used by Android and Desktop. */
@Composable
@Suppress("ViewModelForwarding")
internal fun IosMainApp(controller: IosShellController, uiViewModel: UIViewModel) {
    val shellState by controller.state.collectAsState()
    val initialTab = remember(uiViewModel) { iosInitialDestination(uiViewModel.currentDeviceAddressFlow.value) }
    val multiBackstack = rememberMultiBackstack(initialTab)

    LaunchedEffect(Unit) { controller.refreshHostReadiness() }
    LaunchedEffect(shellState.pendingNavigationUrl) {
        shellState.pendingNavigationUrl?.let { url ->
            uiViewModel.handleDeepLink(CommonUri.parse(url))
            controller.consumeNavigationUrl(url)
        }
    }

    CompositionLocalProvider(
        LocalPlatformSettingsSection provides
            {
                AppleIntegrationSettingsSection(state = shellState, onRefresh = controller::refreshHostReadiness)
            },
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Image(
                painter = painterResource(Res.drawable.img_ntsocial_background_butterfly),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(BACKGROUND_ALPHA),
            )
            IosNavigationShell(
                multiBackstack = multiBackstack,
                uiViewModel = uiViewModel,
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            )
        }
    }
}

@Composable
@Suppress("ViewModelForwarding")
@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
private fun IosNavigationShell(
    multiBackstack: MultiBackstack,
    uiViewModel: UIViewModel,
    modifier: Modifier = Modifier,
) {
    val fleetManager = koinInject<RadioFleetManager>()
    val snapshots by fleetManager.snapshots.collectAsState()
    val selectedEndpointId by fleetManager.selectedEndpointId.collectAsState()
    val uiAction = rememberUiAction()
    val scopeMode = iosFeatureScopeMode(multiBackstack)
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
    val backStack = multiBackstack.activeBackStack
    val showEndpointTabs = scopeMode == IosFeatureScopeMode.SELECTED_ENDPOINT && endpointList.isNotEmpty()
    MeshtasticAppShell(
        multiBackstack = multiBackstack,
        uiViewModel = uiViewModel,
        sharedDialogs = { IosImportDialogs(snapshots) },
    ) {
        MeshtasticNavigationSuite(
            multiBackstack = multiBackstack,
            uiViewModel = uiViewModel,
            radioState = rememberFleetNavigationState(),
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = NAVIGATION_SURFACE_ALPHA),
        ) {
            Column(modifier = Modifier.fillMaxSize().endpointTabsPadding(showEndpointTabs)) {
                if (showEndpointTabs) {
                    IosRadioEndpointTabs(
                        endpoints = endpointList,
                        selectedEndpointId = selectedEndpointId,
                        onSelect = { endpointId ->
                            if (endpointId != selectedEndpointId) {
                                uiAction { fleetManager.select(endpointId) }
                            }
                        },
                    )
                }
                Box(Modifier.weight(1F)) {
                    IosRadioNavigationDisplay(
                        multiBackstack = multiBackstack,
                        backStack = backStack,
                        uiViewModel = uiViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun IosRadioNavigationDisplay(
    multiBackstack: MultiBackstack,
    backStack: NavBackStack<NavKey>,
    uiViewModel: UIViewModel,
) {
    val fleetManager = koinInject<RadioFleetManager>()
    val provider =
        entryProvider<NavKey> {
            contactsGraph(
                backStack = backStack,
                scrollToTopEvents = uiViewModel.scrollToTopEventFlow,
                fleetRootContent = { FleetChannelsEntry(multiBackstack, fleetManager) },
                endpointContent = { endpointId, expectedGeneration, content ->
                    IosEndpointScopeHost(
                        endpointId = RadioEndpointId(endpointId),
                        expectedGeneration = expectedGeneration,
                        content = content,
                    )
                },
            )
            nodesGraph(
                backStack = backStack,
                scrollToTopEvents = uiViewModel.scrollToTopEventFlow,
                onHandleDeepLink = uiViewModel::handleDeepLink,
                onNavigateToConnections = { multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route) },
            )
            meshCoreGraph(backStack)
            settingsGraph(backStack)
            connectionsGraph(backStack)
            channelsGraph(backStack)
            wifiProvisionGraph(backStack)
        }
    MeshtasticNavDisplay(
        multiBackstack = multiBackstack,
        entryProvider =
        endpointEntryProvider(
            provider = provider,
            onNavigateToConnections = { multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route) },
        ) { target, content ->
            IosEndpointScopeHost(
                RadioEndpointId(target.endpointId),
                expectedRuntimeId = target.runtimeId,
                content = content,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
private fun IosEndpointScopeHost(
    endpointId: RadioEndpointId,
    expectedGeneration: Long? = null,
    expectedRuntimeId: String? = null,
    content: @Composable () -> Unit,
) {
    val fleetManager = koinInject<RadioFleetManager>()
    val scopeRegistry = koinInject<IosRadioEndpointScopeRegistry>()
    val snapshots by fleetManager.snapshots.collectAsState()
    val endpointScopes by scopeRegistry.scopes.collectAsState()
    val snapshot = snapshots[endpointId]
    val generationMatches = expectedGeneration == null || snapshot?.generation == expectedGeneration
    val endpointScope = endpointScopes[endpointId]
    if (snapshot?.profile?.enabled == true && endpointScope?.isNotClosed() == true) {
        remember(endpointScope, snapshot.runtimeId) {
            endpointScope.declare(EndpointTarget(endpointId.value, snapshot.runtimeId))
        }
    }

    when {
        snapshot == null ||
            !snapshot.profile.enabled ||
            !generationMatches ||
            (expectedRuntimeId != null && expectedRuntimeId != snapshot.runtimeId) ->
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    stringResource(Res.string.channel_hub_endpoint_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

        endpointScope?.isNotClosed() == true ->
            UnboundKoinScope(scope = endpointScope) { key(snapshot.runtimeId) { content() } }

        else -> IosEndpointLoadingState(Modifier.fillMaxSize())
    }
}

@Composable
private fun IosEndpointLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.radio_endpoint_switching), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IosRadioEndpointTabs(
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(iosEndpointTabStateColor(endpoint.state)))
                        Spacer(Modifier.width(7.dp))
                        Text("${endpoint.profile.displayName} · ${endpoint.profile.addressSuffix.uppercase()}")
                    }
                },
            )
        }
    }
}

private enum class IosFeatureScopeMode {
    ROOT,
    SELECTED_ENDPOINT,
    FLEET,
}

private fun iosFeatureScopeMode(multiBackstack: MultiBackstack): IosFeatureScopeMode {
    val currentTopLevel = multiBackstack.currentTabRoute
    return if (
        (currentTopLevel == TopLevelDestination.Connections.route && multiBackstack.activeBackStack.size == 1) ||
        currentTopLevel == TopLevelDestination.MeshCore.route
    ) {
        IosFeatureScopeMode.ROOT
    } else if (currentTopLevel != TopLevelDestination.Conversations.route) {
        IosFeatureScopeMode.SELECTED_ENDPOINT
    } else {
        when (multiBackstack.activeBackStack.lastOrNull()?.withoutEndpoint()) {
            ContactsRoute.ContactsGraph,
            ContactsRoute.Contacts,
            -> IosFeatureScopeMode.FLEET

            else -> IosFeatureScopeMode.SELECTED_ENDPOINT
        }
    }
}

@Composable
private fun iosEndpointTabStateColor(state: EndpointSessionState): Color = when (state) {
    is EndpointSessionState.Ready -> Color(IOS_ENDPOINT_READY_COLOR)

    EndpointSessionState.Connecting,
    EndpointSessionState.Synchronizing,
    -> Color(IOS_ENDPOINT_CONNECTING_COLOR)

    is EndpointSessionState.Degraded,
    is EndpointSessionState.Failed,
    -> Color(IOS_ENDPOINT_ATTENTION_COLOR)

    EndpointSessionState.Releasing,
    EndpointSessionState.Registered,
    EndpointSessionState.WaitingResource,
    -> MaterialTheme.colorScheme.outline
}

internal fun iosInitialDestination(selectedDeviceAddress: String?): NavKey =
    if (selectedDeviceAddress.isNullOrSelectedNone()) {
        TopLevelDestination.Connections.route
    } else {
        NodesRoute.NodesGraph
    }

private fun String?.isNullOrSelectedNone(): Boolean = isNullOrBlank() || this == "n"

private const val BACKGROUND_ALPHA = 0.08F
private const val NAVIGATION_SURFACE_ALPHA = 0.96F
private const val IOS_ENDPOINT_READY_COLOR = 0xff16a34a
private const val IOS_ENDPOINT_CONNECTING_COLOR = 0xff2563eb
private const val IOS_ENDPOINT_ATTENTION_COLOR = 0xffd97706
internal const val SURFACE_ALPHA = 0.94F

@Composable
private fun IosImportDialogs(snapshots: Map<RadioEndpointId, RadioEndpointSnapshot>) {
    val uiViewModel: UIViewModel = koinViewModel()
    FleetImportDialogs(
        viewModel = uiViewModel,
        options =
        snapshots.values
            .filter { it.profile.enabled }
            .map {
                EndpointImportOption(
                    EndpointTarget(it.profile.id.value, it.runtimeId),
                    "${it.profile.displayName} · ${it.profile.addressSuffix.uppercase()}",
                    it.state is EndpointSessionState.Ready,
                )
            },
    ) { owner, content ->
        IosEndpointScopeHost(RadioEndpointId(owner.endpointId), expectedRuntimeId = owner.runtimeId, content = content)
    }
}
