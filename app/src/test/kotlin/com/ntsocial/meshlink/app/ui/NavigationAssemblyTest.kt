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
package com.ntsocial.meshlink.app.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ntsocial.meshlink.core.navigation.ChannelsRoute
import com.ntsocial.meshlink.core.navigation.ConnectionsRoute
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.FirmwareRoute
import com.ntsocial.meshlink.core.navigation.MeshCoreRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.feature.connections.navigation.connectionsGraph
import com.ntsocial.meshlink.feature.firmware.navigation.firmwareGraph
import com.ntsocial.meshlink.feature.meshcore.navigation.meshCoreGraph
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationAssemblyTest {

    @Test
    fun verifyNavigationGraphsAssembleWithoutCrashing() {
        val backStack = NavBackStack<NavKey>(NodesRoute.NodesGraph)
        val provider =
            entryProvider<NavKey> {
                contactsGraph(backStack, emptyFlow())
                nodesGraph(backStack = backStack, scrollToTopEvents = emptyFlow())
                meshCoreGraph(backStack)
                channelsGraph(backStack)
                connectionsGraph(backStack)
                settingsGraph(backStack)
                firmwareGraph(backStack)
            }

        val topLevelRoutes =
            listOf(
                ContactsRoute.ContactsGraph,
                NodesRoute.NodesGraph,
                MeshCoreRoute.MeshCoreGraph,
                ChannelsRoute.ChannelsGraph,
                ConnectionsRoute.ConnectionsGraph,
                SettingsRoute.SettingsGraph(),
                FirmwareRoute.FirmwareGraph,
            )

        topLevelRoutes.forEach { route -> assertNotNull(provider(route)) }
    }
}
