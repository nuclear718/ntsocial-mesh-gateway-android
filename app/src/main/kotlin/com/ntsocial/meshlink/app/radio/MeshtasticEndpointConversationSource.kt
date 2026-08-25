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
package com.ntsocial.meshlink.app.radio

import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.util.getChannel
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.conversation.ChannelSecurityKind
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSnapshot
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSource
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelKey
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelRole
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelSummary
import com.ntsocial.meshlink.core.radiofleet.conversation.LocalChannelId
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings

internal class MeshtasticEndpointConversationSource(
    override val endpointId: RadioEndpointId,
    radioConfigRepository: RadioConfigRepository,
    packetRepository: PacketRepository,
    scope: CoroutineScope,
) : EndpointConversationSource {
    override val snapshot =
        radioConfigRepository.channelSetFlow
            .map { channelSet -> ChannelCatalog(channelSet, nowMillis) }
            .flatMapLatest { catalog -> catalog.observeSnapshot(packetRepository) }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = EndpointConversationSnapshot(endpointId),
            )

    private fun ChannelCatalog.observeSnapshot(packetRepository: PacketRepository) = channelSet.settings
        .mapIndexed { index, settings -> index to settings }
        .let { configuredChannels ->
            if (configuredChannels.isEmpty()) {
                flowOf(
                    EndpointConversationSnapshot(
                        endpointId = endpointId,
                        lastSuccessfulSyncAtMillis = loadedAtMillis,
                        hasCachedCatalog = false,
                    ),
                )
            } else {
                val unreadFlows =
                    configuredChannels.map { (index) -> packetRepository.getUnreadCountFlow(contactKey(index)) }
                combine(
                    packetRepository.getContacts(),
                    packetRepository.getContactSettings(),
                    combine(unreadFlows) { values -> values.toList() },
                ) { contacts, contactSettings, unreadCounts ->
                    EndpointConversationSnapshot(
                        endpointId = endpointId,
                        channels =
                        configuredChannels.mapIndexed { position, (index, settings) ->
                            val localContactKey = contactKey(index)
                            val lastPacket = contacts[localContactKey]
                            FleetChannelSummary(
                                key = FleetChannelKey(endpointId, LocalChannelId("meshtastic:$index")),
                                localContactKey = localContactKey,
                                channelIndex = index,
                                name = channelSet.getChannel(index)?.name ?: "Channel $index",
                                role =
                                if (index == 0) {
                                    FleetChannelRole.PRIMARY
                                } else {
                                    FleetChannelRole.SECONDARY
                                },
                                security = settings.securityKind(),
                                unreadCount = unreadCounts[position],
                                lastMessageText = lastPacket?.text,
                                lastMessageAtMillis = lastPacket?.time?.takeUnless { it == 0L },
                                isMuted = contactSettings[localContactKey]?.isMuted == true,
                            )
                        },
                        lastSuccessfulSyncAtMillis = loadedAtMillis,
                        hasCachedCatalog = true,
                    )
                }
            }
        }

    private fun ChannelSettings.securityKind(): ChannelSecurityKind = when {
        psk.size == 0 || (psk.size == 1 && psk[0].toInt() == 0) -> ChannelSecurityKind.CLEAR

        psk.size == 1 && (psk[0].toInt() and UNSIGNED_BYTE_MASK) in WELL_KNOWN_PSK_RANGE ->
            ChannelSecurityKind.WELL_KNOWN

        else -> ChannelSecurityKind.CUSTOM
    }

    private fun contactKey(index: Int): String = "$index${DataPacket.ID_BROADCAST}"

    private data class ChannelCatalog(val channelSet: ChannelSet, val loadedAtMillis: Long)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val UNSIGNED_BYTE_MASK = 0xff
        val WELL_KNOWN_PSK_RANGE = 1..10
    }
}
