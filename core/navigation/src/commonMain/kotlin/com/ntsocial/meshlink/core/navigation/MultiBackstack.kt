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
package com.ntsocial.meshlink.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/** Manages independent backstacks for multiple tabs. */
class MultiBackstack(val startTab: NavKey) {
    var backStacks: Map<NavKey, NavBackStack<NavKey>> = emptyMap()

    var currentTabRoute: NavKey by mutableStateOf(TopLevelDestination.fromNavKey(startTab)?.route ?: startTab)
        private set

    val activeBackStack: NavBackStack<NavKey>
        get() = backStacks[currentTabRoute] ?: error("Stack for $currentTabRoute not found")

    var endpointTarget: EndpointTarget? by mutableStateOf(null)
        private set

    private var endpointBindingInitialized = false

    /** One selection policy for every caller, including Hub and Connections, and every inactive stack. */
    fun bindEndpoint(target: EndpointTarget?) {
        if (endpointBindingInitialized && target == endpointTarget) return
        endpointBindingInitialized = true
        val previous = endpointTarget
        endpointTarget = target
        backStacks.forEach { (root, stack) ->
            val path = stack.map { it.withoutEndpoint() }
            val last = path.lastOrNull()
            val retained =
                when {
                    previous == null || target == null || root != currentTabRoute -> emptyList()
                    last is ChannelsRoute -> listOf(ChannelsRoute.ChannelsGraph)
                    last is SettingsRoute && path.none(::isRemoteNodeDestination) -> path.drop(1)
                    else -> emptyList()
                }
            stack.replaceAll((listOf(root) + retained).map(::bindDestination))
        }
    }

    fun bindDestination(destination: NavKey): NavKey = endpointTarget
        // Navigation 3 caches entries by the backstack keys, not by a changing entry provider. Connections keeps
        // its global management destination, but its selected-radio content must carry the actual runtime key.
        ?.takeIf { destination.isRadioDestination() || destination.withoutEndpoint() is ConnectionsRoute }
        ?.let { EndpointRoute.Destination(it, destination.withoutEndpoint()) } ?: destination

    /** Switches to a new top-level tab route. */
    fun navigateTopLevel(route: NavKey) {
        val rootKey = TopLevelDestination.fromNavKey(route)?.route ?: route

        if (currentTabRoute == rootKey) {
            // Repressing the same tab resets its stack to just the root
            activeBackStack.replaceAll(listOf(bindDestination(rootKey)))
        } else {
            // Switching to a different tab
            currentTabRoute = rootKey
        }
    }

    /** Handles back navigation according to the "exit through home" pattern. */
    fun goBack() {
        val currentStack = activeBackStack
        if (currentStack.size > 1) {
            currentStack.removeLastOrNull()
            return
        }

        // If we're at the root of a non-start tab, switch back to the start tab
        if (currentTabRoute != startTab) {
            currentTabRoute = startTab
        }
    }

    /**
     * Applies a deep-link path without assigning a nested graph key as the active top-level tab.
     *
     * A path rooted at a real top-level destination replaces that destination's stack. A path rooted at a nested graph,
     * such as Channels or Firmware, is appended to the current tab just like in-app navigation.
     */
    fun handleDeepLink(navKeys: List<NavKey>) {
        val rootKey = navKeys.firstOrNull() ?: return
        val topLevel = TopLevelDestination.fromNavKey(rootKey)?.route
        if (topLevel != null) {
            val stack = backStacks[topLevel] ?: return
            stack.replaceAll(navKeys.map(::bindDestination))
            currentTabRoute = topLevel
        } else {
            backStacks[currentTabRoute]?.addAll(navKeys.map(::bindDestination))
        }
    }
}

private fun isRemoteNodeDestination(destination: NavKey): Boolean = when (destination) {
    is NodesRoute.NodeDetail,
    is NodeDetailRoute,
    -> true

    is SettingsRoute.Settings -> destination.destNum != null

    is SettingsRoute.SettingsGraph -> destination.destNum != null

    else -> false
}

/** Remembers a [MultiBackstack] for managing independent tab navigation histories with Navigation 3. */
@Composable
fun rememberMultiBackstack(initialTab: NavKey = TopLevelDestination.Connections.route): MultiBackstack {
    val stacks = mutableMapOf<NavKey, NavBackStack<NavKey>>()

    TopLevelDestination.entries.forEach { dest ->
        key(dest.route) { stacks[dest.route] = rememberNavBackStack(MeshtasticNavSavedStateConfig, dest.route) }
    }

    val multiBackstack = remember { MultiBackstack(initialTab) }
    multiBackstack.backStacks = stacks

    return multiBackstack
}

/** Fleet and connection management roots are global; their actions supply an explicit endpoint target. */
fun NavKey.isRadioDestination(): Boolean = when (withoutEndpoint()) {
    ContactsRoute.ContactsGraph,
    ContactsRoute.Contacts,
    ConnectionsRoute.ConnectionsGraph,
    ConnectionsRoute.Connections,
    -> false

    else -> withoutEndpoint() !is MeshCoreRoute && withoutEndpoint() !is WifiProvisionRoute
}
