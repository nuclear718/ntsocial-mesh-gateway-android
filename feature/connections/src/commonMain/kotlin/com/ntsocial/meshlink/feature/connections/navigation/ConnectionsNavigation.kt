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
package com.ntsocial.meshlink.feature.connections.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.ConnectionsRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.feature.connections.ScannerViewModel
import com.ntsocial.meshlink.feature.connections.ui.ConnectionsScreen
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Navigation graph for for the top level ConnectionsScreen - [ConnectionsRoute.Connections]. */
fun EntryProviderScope<NavKey>.connectionsGraph(backStack: NavBackStack<NavKey>) {
    entry<ConnectionsRoute.ConnectionsGraph> {
        ConnectionsScreen(
            scanModel = koinViewModel<ScannerViewModel>(),
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onClickNodeChip = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onNavigateToNodeDetails = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }

    entry<ConnectionsRoute.Connections> {
        ConnectionsScreen(
            scanModel = koinViewModel<ScannerViewModel>(),
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onClickNodeChip = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onNavigateToNodeDetails = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }
}
