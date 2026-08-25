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

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.common.util.CommonUri
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiBackstackTest {

    @Test
    fun `navigateTopLevel to different tab preserves previous tab stack and activates new tab stack`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        val meshCoreStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.MeshCore.route)) }

        multiBackstack.backStacks =
            mapOf(TopLevelDestination.Nodes.route to nodesStack, TopLevelDestination.MeshCore.route to meshCoreStack)

        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.currentTabRoute)
        assertEquals(2, multiBackstack.activeBackStack.size)

        multiBackstack.navigateTopLevel(TopLevelDestination.MeshCore.route)

        assertEquals(TopLevelDestination.MeshCore.route, multiBackstack.currentTabRoute)
        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(2, nodesStack.size)
    }

    @Test
    fun `navigateTopLevel to same tab resets stack to root`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Nodes.route to nodesStack)

        assertEquals(2, multiBackstack.activeBackStack.size)

        multiBackstack.navigateTopLevel(TopLevelDestination.Nodes.route)

        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.activeBackStack.first())
    }

    @Test
    fun `goBack pops current stack if size is greater than 1`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val nodesStack =
            NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route, NodesRoute.Nodes)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Nodes.route to nodesStack)

        multiBackstack.goBack()

        assertEquals(1, multiBackstack.activeBackStack.size)
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.activeBackStack.first())
    }

    @Test
    fun `goBack on root of non-start tab returns to start tab`() {
        val startTab = TopLevelDestination.Connections.route
        val multiBackstack = MultiBackstack(startTab)

        val meshCoreStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.MeshCore.route)) }
        val connectionsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connections.route)) }

        multiBackstack.backStacks =
            mapOf(
                TopLevelDestination.MeshCore.route to meshCoreStack,
                TopLevelDestination.Connections.route to connectionsStack,
            )

        multiBackstack.navigateTopLevel(TopLevelDestination.MeshCore.route)
        assertEquals(TopLevelDestination.MeshCore.route, multiBackstack.currentTabRoute)

        multiBackstack.goBack()

        assertEquals(TopLevelDestination.Connections.route, multiBackstack.currentTabRoute)
    }

    @Test
    fun `handleDeepLink sets target tab and populates stack`() {
        val startTab = TopLevelDestination.Nodes.route
        val multiBackstack = MultiBackstack(startTab)

        val settingsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Settings.route)) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Settings.route to settingsStack)

        val deepLinkPath = listOf(TopLevelDestination.Settings.route, SettingsRoute.About)
        multiBackstack.handleDeepLink(deepLinkPath)

        assertEquals(TopLevelDestination.Settings.route, multiBackstack.currentTabRoute)
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(SettingsRoute.About, multiBackstack.activeBackStack.last())
    }

    @Test
    fun `handleDeepLink from different tab switches tab and sets stack`() {
        // Start on Connections tab
        val startTab = TopLevelDestination.Connections.route
        val multiBackstack = MultiBackstack(startTab)

        val connectionsStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Connections.route)) }
        val nodesStack = NavBackStack<NavKey>().apply { addAll(listOf(TopLevelDestination.Nodes.route)) }

        multiBackstack.backStacks =
            mapOf(
                TopLevelDestination.Connections.route to connectionsStack,
                TopLevelDestination.Nodes.route to nodesStack,
            )

        // Verify we start on Connections
        assertEquals(TopLevelDestination.Connections.route, multiBackstack.currentTabRoute)

        val tracerouteLog = NodeDetailRoute.TracerouteLog(destNum = 100)
        multiBackstack.handleDeepLink(listOf(NodesRoute.NodesGraph, tracerouteLog))

        // Should have switched to the Nodes tab
        assertEquals(TopLevelDestination.Nodes.route, multiBackstack.currentTabRoute)
        // Stack should contain the graph root + the traceroute log route.
        assertEquals(2, multiBackstack.activeBackStack.size)
        assertEquals(NodesRoute.NodesGraph, multiBackstack.activeBackStack.first())
        assertEquals(tracerouteLog, multiBackstack.activeBackStack.last())
    }

    @Test
    fun `channels router path stays on current tab and is safe to render`() {
        val startTab = TopLevelDestination.Settings.route
        val multiBackstack = MultiBackstack(startTab)
        val settingsStack = NavBackStack<NavKey>().apply { add(TopLevelDestination.Settings.route) }
        multiBackstack.backStacks = mapOf(TopLevelDestination.Settings.route to settingsStack)
        val path = requireNotNull(DeepLinkRouter.route(CommonUri.parse("meshtastic://meshtastic/channels")))

        multiBackstack.handleDeepLink(path)

        assertEquals(TopLevelDestination.Settings.route, multiBackstack.currentTabRoute)
        assertEquals(
            listOf<NavKey>(TopLevelDestination.Settings.route, ChannelsRoute.ChannelsGraph),
            multiBackstack.activeBackStack.toList(),
        )
    }

    @Test
    fun `firmware and wifi nested paths append to current stack`() {
        val startTab = TopLevelDestination.Connections.route
        val nestedPaths =
            listOf(
                listOf(FirmwareRoute.FirmwareGraph, FirmwareRoute.FirmwareUpdate),
                listOf(WifiProvisionRoute.WifiProvision(address = null)),
            )

        nestedPaths.forEach { path ->
            val multiBackstack = MultiBackstack(startTab)
            val connectionsStack = NavBackStack<NavKey>().apply { add(startTab) }
            multiBackstack.backStacks = mapOf(startTab to connectionsStack)

            multiBackstack.handleDeepLink(path)

            assertEquals(startTab, multiBackstack.currentTabRoute)
            val expected: List<NavKey> = listOf(startTab) + path
            assertEquals(expected, multiBackstack.activeBackStack.toList())
        }
    }

    @Test
    fun `missing top-level target stack does not change current tab`() {
        val startTab = TopLevelDestination.Settings.route
        val multiBackstack = MultiBackstack(startTab)
        val settingsStack = NavBackStack<NavKey>().apply { add(startTab) }
        multiBackstack.backStacks = mapOf(startTab to settingsStack)

        multiBackstack.handleDeepLink(listOf(NodesRoute.NodesGraph))

        assertEquals(startTab, multiBackstack.currentTabRoute)
        assertEquals(listOf<NavKey>(startTab), multiBackstack.activeBackStack.toList())
    }
}
