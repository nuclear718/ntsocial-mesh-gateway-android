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
package com.ntsocial.meshlink.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.feature.connections.navigation.connectionsGraph
import com.ntsocial.meshlink.feature.firmware.navigation.firmwareGraph
import com.ntsocial.meshlink.feature.map.navigation.mapGraph
import com.ntsocial.meshlink.feature.meshcore.navigation.meshCoreGraph
import com.ntsocial.meshlink.feature.messaging.navigation.contactsGraph
import com.ntsocial.meshlink.feature.node.navigation.nodesGraph
import com.ntsocial.meshlink.feature.settings.navigation.settingsGraph
import com.ntsocial.meshlink.feature.settings.radio.channel.channelsGraph
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationAssemblyTest {

    @Test
    fun verifyNavigationGraphsAssembleWithoutCrashing() = runComposeUiTest {
        setContent {
            val backStack = rememberNavBackStack(NodesRoute.NodesGraph)
            entryProvider<NavKey> {
                contactsGraph(backStack, emptyFlow())
                nodesGraph(backStack = backStack, scrollToTopEvents = emptyFlow())
                mapGraph(backStack)
                meshCoreGraph(backStack)
                channelsGraph(backStack)
                connectionsGraph(backStack)
                settingsGraph(backStack)
                firmwareGraph(backStack)
            }
        }
    }
}
