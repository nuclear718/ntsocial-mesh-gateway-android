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

import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import kotlin.jvm.JvmInline

enum class NodeAccentToken {
    INDIGO,
    EMERALD,
    AMBER,
    CYAN,
    BLUE,
    VIOLET,
    ROSE,
    LIME,
    TEAL,
    ORANGE,
    SLATE,
    FUCHSIA,
}

data class EndpointAppearance(
    val accentToken: NodeAccentToken,
    val purposeLabel: String = "",
    val sortOrder: Int = Int.MAX_VALUE,
    val showInAll: Boolean = true,
)

@JvmInline
value class LocalChannelId(val value: String) {
    init {
        require(value.isNotBlank()) { "Local channel ID cannot be blank" }
    }
}

data class FleetChannelKey(val endpointId: RadioEndpointId, val localChannelId: LocalChannelId)

enum class FleetChannelRole {
    PRIMARY,
    SECONDARY,
}

enum class ChannelSecurityKind {
    CLEAR,
    WELL_KNOWN,
    CUSTOM,
}

data class FleetChannelSummary(
    val key: FleetChannelKey,
    val localContactKey: String,
    val channelIndex: Int?,
    val name: String,
    val role: FleetChannelRole,
    val security: ChannelSecurityKind,
    val unreadCount: Int,
    val lastMessageText: String?,
    val lastMessageAtMillis: Long?,
    val isMuted: Boolean,
)

data class EndpointConversationSnapshot(
    val endpointId: RadioEndpointId,
    val channels: List<FleetChannelSummary> = emptyList(),
    val lastSuccessfulSyncAtMillis: Long? = null,
    val hasCachedCatalog: Boolean = false,
)

data class FleetChannelGroup(
    val profile: RadioEndpointProfile,
    val sessionState: EndpointSessionState,
    val generation: Long,
    val appearance: EndpointAppearance,
    val channels: List<FleetChannelSummary>,
    val lastSuccessfulSyncAtMillis: Long?,
    val hasCachedCatalog: Boolean,
    val dataAvailable: Boolean,
) {
    val unreadCount: Int
        get() = channels.sumOf(FleetChannelSummary::unreadCount)

    val canSend: Boolean
        get() = sessionState is EndpointSessionState.Ready
}

private val DEFAULT_ACCENTS =
    listOf(
        NodeAccentToken.INDIGO,
        NodeAccentToken.EMERALD,
        NodeAccentToken.AMBER,
        NodeAccentToken.CYAN,
        NodeAccentToken.BLUE,
        NodeAccentToken.VIOLET,
        NodeAccentToken.ROSE,
        NodeAccentToken.LIME,
        NodeAccentToken.TEAL,
        NodeAccentToken.ORANGE,
        NodeAccentToken.SLATE,
        NodeAccentToken.FUCHSIA,
    )

fun defaultEndpointAppearance(index: Int): EndpointAppearance =
    EndpointAppearance(accentToken = DEFAULT_ACCENTS[index.mod(DEFAULT_ACCENTS.size)], sortOrder = index)
