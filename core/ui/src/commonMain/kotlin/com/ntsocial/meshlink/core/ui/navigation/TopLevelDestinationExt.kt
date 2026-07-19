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

import com.ntsocial.meshlink.core.navigation.TopLevelDestination
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_forum
import com.ntsocial.meshlink.core.resources.ic_hub
import com.ntsocial.meshlink.core.resources.ic_nodes
import com.ntsocial.meshlink.core.resources.ic_settings
import com.ntsocial.meshlink.core.resources.ic_wifi
import org.jetbrains.compose.resources.DrawableResource

/** Maps a shared [TopLevelDestination] to its corresponding icon [DrawableResource]. */
val TopLevelDestination.icon: DrawableResource
    get() =
        when (this) {
            TopLevelDestination.Conversations -> Res.drawable.ic_forum
            TopLevelDestination.Nodes -> Res.drawable.ic_nodes
            TopLevelDestination.MeshCore -> Res.drawable.ic_hub
            TopLevelDestination.Settings -> Res.drawable.ic_settings
            TopLevelDestination.Connections -> Res.drawable.ic_wifi
        }
