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
package com.ntsocial.meshlink.feature.node.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.ChannelsRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.ui.component.ScrollToTopEvent
import com.ntsocial.meshlink.feature.node.list.NodeListScreen
import com.ntsocial.meshlink.feature.node.list.NodeListViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdaptiveNodeListScreen(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    onHandleDeepLink: (com.ntsocial.meshlink.core.common.util.CommonUri, onInvalid: () -> Unit) -> Unit = { _, _ -> },
    onNavigateToConnections: () -> Unit = {},
) {
    val nodeListViewModel: NodeListViewModel = koinViewModel()

    NodeListScreen(
        viewModel = nodeListViewModel,
        navigateToNodeDetails = { nodeId -> backStack.add(NodesRoute.NodeDetail(nodeId)) },
        onNavigateToChannels = { backStack.add(ChannelsRoute.ChannelsGraph) },
        scrollToTopEvents = scrollToTopEvents,
        activeNodeId = null,
        onHandleDeepLink = onHandleDeepLink,
        onNavigateToConnections = onNavigateToConnections,
    )
}
