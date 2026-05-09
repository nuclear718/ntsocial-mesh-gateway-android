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
package com.ntsocial.meshlink.feature.map.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.MapRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute

fun EntryProviderScope<NavKey>.mapGraph(backStack: NavBackStack<NavKey>) {
    entry<MapRoute.Map> { args ->
        val mapScreen = com.ntsocial.meshlink.core.ui.util.LocalMapMainScreenProvider.current
        mapScreen(
            { id -> backStack.add(NodesRoute.NodeDetail(id)) }, // onClickNodeChip
            { id -> backStack.add(NodesRoute.NodeDetail(id)) }, // navigateToNodeDetails
            args.waypointId,
        )
    }
}
