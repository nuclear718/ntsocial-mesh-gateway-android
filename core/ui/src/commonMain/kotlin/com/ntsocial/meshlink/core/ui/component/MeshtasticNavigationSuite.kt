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
package com.ntsocial.meshlink.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.TopLevelDestination
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.connected
import com.ntsocial.meshlink.core.resources.connecting
import com.ntsocial.meshlink.core.resources.device_sleeping
import com.ntsocial.meshlink.core.resources.disconnected
import com.ntsocial.meshlink.core.ui.navigation.icon
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Shared adaptive navigation shell using [NavigationSuiteScaffold].
 *
 * This implementation uses the [MultiBackstack] state holder to manage independent histories for each tab, aligning
 * with Navigation 3 best practices for state preservation during tab switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshtasticNavigationSuite(
    multiBackstack: MultiBackstack,
    uiViewModel: UIViewModel,
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    content: @Composable () -> Unit,
) {
    val connectionState by uiViewModel.connectionState.collectAsStateWithLifecycle()
    val unreadMessageCount by uiViewModel.unreadMessageCount.collectAsStateWithLifecycle()
    val selectedDevice by uiViewModel.currentDeviceAddressFlow.collectAsStateWithLifecycle()

    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    val currentTabRoute = multiBackstack.currentTabRoute
    val topLevelDestination = TopLevelDestination.fromNavKey(currentTabRoute)

    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo).coerceNavigationType()
    val showLabels = layoutType == NavigationSuiteType.NavigationRail

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        containerColor = containerColor,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == topLevelDestination
                item(
                    selected = isSelected,
                    onClick = { handleNavigation(destination, topLevelDestination, multiBackstack, uiViewModel) },
                    icon = {
                        NavigationIconContent(
                            destination = destination,
                            isSelected = isSelected,
                            connectionState = connectionState,
                            unreadMessageCount = unreadMessageCount,
                            selectedDevice = selectedDevice,
                            uiViewModel = uiViewModel,
                        )
                    },
                    label =
                    if (showLabels) {
                        { Text(stringResource(destination.label)) }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        Row { content() }
    }
}

/**
 * Caps [NavigationSuiteType] so that expanded/extra-large widths still use a NavigationRail instead of promoting to a
 * permanent NavigationDrawer.
 */
private fun NavigationSuiteType.coerceNavigationType(): NavigationSuiteType = when (this) {
    NavigationSuiteType.NavigationDrawer -> NavigationSuiteType.NavigationRail
    else -> this
}

private fun handleNavigation(
    destination: TopLevelDestination,
    topLevelDestination: TopLevelDestination?,
    multiBackstack: MultiBackstack,
    uiViewModel: UIViewModel,
) {
    val isRepress = destination == topLevelDestination
    if (isRepress) {
        val currentKey = multiBackstack.activeBackStack.lastOrNull()
        when (destination) {
            TopLevelDestination.Nodes -> {
                val onNodesList = currentKey is NodesRoute.NodesGraph || currentKey is NodesRoute.Nodes
                if (!onNodesList) {
                    multiBackstack.navigateTopLevel(destination.route)
                } else {
                    uiViewModel.emitScrollToTopEvent(ScrollToTopEvent.NodesTabPressed)
                }
            }

            TopLevelDestination.Conversations -> {
                val onConversationsList =
                    currentKey is ContactsRoute.ContactsGraph || currentKey is ContactsRoute.Contacts
                if (!onConversationsList) {
                    multiBackstack.navigateTopLevel(destination.route)
                } else {
                    uiViewModel.emitScrollToTopEvent(ScrollToTopEvent.ConversationsTabPressed)
                }
            }

            else -> {
                if (currentKey != destination.route) {
                    multiBackstack.navigateTopLevel(destination.route)
                }
            }
        }
    } else {
        multiBackstack.navigateTopLevel(destination.route)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationIconContent(
    destination: TopLevelDestination,
    isSelected: Boolean,
    connectionState: ConnectionState,
    unreadMessageCount: Int,
    selectedDevice: String?,
    uiViewModel: UIViewModel,
) {
    val isConnectionsRoute = destination == TopLevelDestination.Connections

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(
                    if (isConnectionsRoute) {
                        when (connectionState) {
                            ConnectionState.Connected -> stringResource(Res.string.connected)
                            ConnectionState.Connecting -> stringResource(Res.string.connecting)
                            ConnectionState.DeviceSleep -> stringResource(Res.string.device_sleeping)
                            ConnectionState.Disconnected -> stringResource(Res.string.disconnected)
                        }
                    } else {
                        stringResource(destination.label)
                    },
                )
            }
        },
        state = rememberTooltipState(),
    ) {
        if (isConnectionsRoute) {
            AnimatedConnectionsNavIcon(
                connectionState = connectionState,
                deviceType = DeviceType.fromAddress(selectedDevice ?: "NoDevice"),
                meshActivityFlow = uiViewModel.meshActivity,
            )
        } else {
            BadgedBox(
                badge = {
                    if (destination == TopLevelDestination.Conversations) {
                        var lastNonZeroCount by remember { mutableIntStateOf(unreadMessageCount) }
                        if (unreadMessageCount > 0) {
                            lastNonZeroCount = unreadMessageCount
                        }
                        AnimatedVisibility(
                            visible = unreadMessageCount > 0,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            Badge { Text(lastNonZeroCount.toString()) }
                        }
                    }
                },
            ) {
                Crossfade(isSelected, label = "BottomBarIcon") { isSelectedState ->
                    Icon(
                        imageVector = vectorResource(destination.icon),
                        contentDescription = stringResource(destination.label),
                        tint = if (isSelectedState) colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
        }
    }
}
