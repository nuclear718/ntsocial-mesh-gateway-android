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
package com.ntsocial.meshlink.desktop.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.navigation.TopLevelDestination
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.feature.connections.navigation.connectionsGraph
import com.ntsocial.meshlink.feature.firmware.navigation.firmwareGraph
import com.ntsocial.meshlink.feature.map.navigation.mapGraph
import com.ntsocial.meshlink.feature.meshcore.navigation.meshCoreGraph
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import com.ntsocial.meshlink.feature.wifiprovision.navigation.wifiProvisionGraph

/**
 * Registers [NavKey] entry providers for every desktop destination.
 *
 * Each call delegates to the shared navigation graph extension exported by the corresponding feature module, keeping
 * the desktop shell free of screen-level composable knowledge.
 */
fun EntryProviderScope<NavKey>.desktopNavGraph(
    backStack: NavBackStack<NavKey>,
    uiViewModel: UIViewModel,
    multiBackstack: MultiBackstack,
) {
    nodesGraph(
        backStack = backStack,
        scrollToTopEvents = uiViewModel.scrollToTopEventFlow,
        onHandleDeepLink = uiViewModel::handleDeepLink,
        onNavigateToConnections = { multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route) },
    )
    contactsGraph(backStack, uiViewModel.scrollToTopEventFlow)
    mapGraph(backStack)
    meshCoreGraph(backStack)
    firmwareGraph(backStack)
    settingsGraph(backStack)
    channelsGraph(backStack)
    connectionsGraph(backStack)
    wifiProvisionGraph(backStack)
}
