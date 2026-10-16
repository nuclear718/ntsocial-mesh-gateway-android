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
package com.ntsocial.meshlink.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val TWO_PANE_MIN_WIDTH_DP = 600

/** All compact-screen sections share one scroll owner, so tall fleet cards cannot starve the device list. */
@Composable
internal fun ConnectionsScreenLayout(
    fleetContent: @Composable () -> Unit,
    statusContent: @Composable () -> Unit,
    deviceListContent: @Composable (LazyListScope.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leadingItems: LazyListScope.() -> Unit = {
        item(key = "connections:fleet") { fleetContent() }
        item(key = "connections:status") { Column { statusContent() } }
    }
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < TWO_PANE_MIN_WIDTH_DP.dp
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!compact) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = leadingItems,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                deviceListContent(if (compact) leadingItems else ({}))
            }
        }
    }
}
