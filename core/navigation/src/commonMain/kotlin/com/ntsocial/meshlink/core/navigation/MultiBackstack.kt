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

    /** Switches to a new top-level tab route. */
    fun navigateTopLevel(route: NavKey) {
        val rootKey = TopLevelDestination.fromNavKey(route)?.route ?: route

        if (currentTabRoute == rootKey) {
            // Repressing the same tab resets its stack to just the root
            activeBackStack.replaceAll(listOf(rootKey))
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
            stack.replaceAll(navKeys)
            currentTabRoute = topLevel
        } else {
            backStacks[currentTabRoute]?.addAll(navKeys)
        }
    }
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
