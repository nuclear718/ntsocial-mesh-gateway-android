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
package com.ntsocial.meshlink.core.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import com.ntsocial.meshlink.core.navigation.ConnectionsRoute
import com.ntsocial.meshlink.core.navigation.EndpointTarget
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import kotlin.test.Test

private val LocalConnectionsOwner = staticCompositionLocalOf { "disconnected" }

@OptIn(ExperimentalTestApi::class)
class ConnectionsEntryOwnershipTest {
    @Test
    fun `cached navigation entry releases and rebinds connections without leaving the page`() = runComposeUiTest {
        val root = ConnectionsRoute.ConnectionsGraph
        val navigation = MultiBackstack(root)
        navigation.backStacks = mapOf(root to NavBackStack<NavKey>(root))
        navigation.bindEndpoint(EndpointTarget("A", "runtime-A"))

        setContent {
            // Exercise Navigation 3's real backstack-key cache. A changing provider/contentKey alone does not
            // invalidate its remembered entries, which previously left Connections inside the retired scope.
            val entries =
                rememberDecoratedNavEntries(
                    backStack = navigation.activeBackStack,
                    entryProvider =
                    endpointEntryProvider(
                        provider = { key ->
                            NavEntry(key) { Text("connections:${LocalConnectionsOwner.current}") }
                        },
                        onNavigateToConnections = {},
                    ) { target, content ->
                        CompositionLocalProvider(LocalConnectionsOwner provides target.runtimeId) { content() }
                    },
                )
            entries.single().Content()
        }

        onNodeWithText("connections:runtime-A").assertIsDisplayed()
        runOnIdle { navigation.bindEndpoint(null) }
        onNodeWithText("connections:disconnected").assertIsDisplayed()
        onNodeWithText("connections:runtime-A").assertDoesNotExist()
        runOnIdle { navigation.bindEndpoint(EndpointTarget("A", "runtime-A2")) }
        onNodeWithText("connections:runtime-A2").assertIsDisplayed()
        runOnIdle { navigation.bindEndpoint(EndpointTarget("B", "runtime-B")) }
        onNodeWithText("connections:runtime-B").assertIsDisplayed()
        onNodeWithText("connections:runtime-A2").assertDoesNotExist()
        runOnIdle { navigation.bindEndpoint(null) }
        onNodeWithText("connections:disconnected").assertIsDisplayed()
    }
}
