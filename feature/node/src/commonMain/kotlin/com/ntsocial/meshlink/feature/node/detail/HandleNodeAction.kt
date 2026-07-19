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
package com.ntsocial.meshlink.feature.node.detail

import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.feature.node.component.NodeMenuAction
import com.ntsocial.meshlink.feature.node.model.NodeDetailAction

/**
 * Shared handler for [NodeDetailAction]s that are common across all platforms.
 *
 * Platform-specific actions (e.g. [NodeDetailAction.ShareContact], [NodeDetailAction.OpenCompass]) are ignored by this
 * handler and should be handled by the platform-specific caller.
 */
internal fun handleNodeAction(
    action: NodeDetailAction,
    uiState: NodeDetailUiState,
    navigateToMessages: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: NodeDetailViewModel,
) {
    when (action) {
        is NodeDetailAction.Navigate -> onNavigate(action.route)

        is NodeDetailAction.TriggerServiceAction -> viewModel.onServiceAction(action.action)

        is NodeDetailAction.OpenRemoteAdmin -> viewModel.openRemoteAdmin(action.nodeNum)

        is NodeDetailAction.RefreshMetadata -> viewModel.refreshMetadata(action.nodeNum)

        is NodeDetailAction.HandleNodeMenuAction -> {
            when (val menuAction = action.action) {
                is NodeMenuAction.DirectMessage -> {
                    val route = viewModel.getDirectMessageRoute(menuAction.node, uiState.ourNode)
                    navigateToMessages(route)
                }

                is NodeMenuAction.Remove -> viewModel.handleNodeMenuAction(menuAction, onNavigateUp)

                else -> viewModel.handleNodeMenuAction(menuAction)
            }
        }

        else -> {}
    }
}
