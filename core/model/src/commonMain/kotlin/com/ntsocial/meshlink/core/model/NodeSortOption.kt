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
package com.ntsocial.meshlink.core.model

import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.node_sort_alpha
import com.ntsocial.meshlink.core.resources.node_sort_channel
import com.ntsocial.meshlink.core.resources.node_sort_distance
import com.ntsocial.meshlink.core.resources.node_sort_hops_away
import com.ntsocial.meshlink.core.resources.node_sort_last_heard
import com.ntsocial.meshlink.core.resources.node_sort_via_favorite
import com.ntsocial.meshlink.core.resources.node_sort_via_mqtt
import org.jetbrains.compose.resources.StringResource

enum class NodeSortOption(val sqlValue: String, val stringRes: StringResource) {
    LAST_HEARD("last_heard", Res.string.node_sort_last_heard),
    ALPHABETICAL("alpha", Res.string.node_sort_alpha),
    DISTANCE("distance", Res.string.node_sort_distance),
    HOPS_AWAY("hops_away", Res.string.node_sort_hops_away),
    CHANNEL("channel", Res.string.node_sort_channel),
    VIA_MQTT("via_mqtt", Res.string.node_sort_via_mqtt),
    VIA_FAVORITE("via_favorite", Res.string.node_sort_via_favorite),
}
