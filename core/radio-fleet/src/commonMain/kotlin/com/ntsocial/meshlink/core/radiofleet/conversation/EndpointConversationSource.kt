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
package com.ntsocial.meshlink.core.radiofleet.conversation

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import kotlinx.coroutines.flow.StateFlow

interface EndpointConversationSource {
    val endpointId: RadioEndpointId
    val snapshot: StateFlow<EndpointConversationSnapshot>
}

data class RegisteredEndpointConversationSource(val runtimeToken: String, val source: EndpointConversationSource)

interface EndpointConversationSourceRegistry {
    val entries: StateFlow<Map<RadioEndpointId, RegisteredEndpointConversationSource>>
}

interface MutableEndpointConversationSourceRegistry : EndpointConversationSourceRegistry {
    fun register(runtimeToken: String, source: EndpointConversationSource)

    fun unregister(endpointId: RadioEndpointId, runtimeToken: String)
}

interface FleetChannelsRepository {
    val groups: StateFlow<List<FleetChannelGroup>>
}
