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
package com.ntsocial.meshlink.core.model

import okio.ByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/** Removes QR padding and semantic duplicates before a full radio replacement. */
fun normalizeReliableChannelSettings(
    settings: List<ChannelSettings>,
    loraConfig: Config.LoRaConfig?,
): List<ChannelSettings> {
    if (settings.isEmpty()) return emptyList()
    val effectiveLora = loraConfig ?: Config.LoRaConfig()
    val primary = settings.first()
    val seen = mutableSetOf<ReliableChannelIdentity>()
    if (!primary.isChannelPlaceholder()) seen += primary.reliableIdentity(effectiveLora)

    return buildList {
        add(primary)
        settings.drop(1).forEach { candidate ->
            if (!candidate.isChannelPlaceholder() && seen.add(candidate.reliableIdentity(effectiveLora))) {
                add(candidate)
            }
        }
    }
}

/** Emits every firmware slot so a replacement cannot leave stale secondary channels behind. */
fun buildAuthoritativeChannelWrites(settings: List<ChannelSettings>, maxChannels: Int): List<Channel> {
    require(maxChannels > 0) { "maxChannels must be positive" }
    require(settings.isNotEmpty()) { "A primary channel is required" }
    require(settings.size <= maxChannels) { "Channel set exceeds radio capacity" }
    return List(maxChannels) { index ->
        Channel(
            index = index,
            role =
            when {
                index == 0 -> Channel.Role.PRIMARY
                index < settings.size -> Channel.Role.SECONDARY
                else -> Channel.Role.DISABLED
            },
            settings = settings.getOrNull(index) ?: ChannelSettings(),
        )
    }
}

/** Returns only the protected secondary slots that are absent from an otherwise safe readback. */
fun buildMissingSecondaryWrites(
    protectedSettings: List<ChannelSettings>,
    currentSettings: List<ChannelSettings>,
): List<Channel> = protectedSettings.mapIndexedNotNull { index, expected ->
    if (index == 0 || currentSettings.getOrNull(index) == expected) {
        null
    } else {
        Channel(index = index, role = Channel.Role.SECONDARY, settings = expected)
    }
}

private fun ChannelSettings.isChannelPlaceholder(): Boolean = name.isNullOrBlank() && psk.size == 0

private data class ReliableChannelIdentity(val name: String, val psk: ByteString)

private fun ChannelSettings.reliableIdentity(loraConfig: Config.LoRaConfig): ReliableChannelIdentity {
    val channel = com.ntsocial.meshlink.core.model.Channel(settings = this, loraConfig = loraConfig)
    return ReliableChannelIdentity(name = channel.name, psk = channel.psk)
}
