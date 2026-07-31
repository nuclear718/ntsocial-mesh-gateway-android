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
package com.ntsocial.meshlink.core.ui.util

import androidx.compose.runtime.Composable
import com.ntsocial.meshlink.core.common.util.DateFormatter
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.unknown_age
import okio.ByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import kotlin.time.Duration.Companion.days
import com.ntsocial.meshlink.core.model.Channel as ModelChannel

private const val SECONDS_TO_MILLIS = 1000L

@Composable
fun Position.formatPositionTime(): String {
    val currentTime = nowMillis
    val sixMonthsAgo = currentTime - 180.days.inWholeMilliseconds
    val isOlderThanSixMonths = time * SECONDS_TO_MILLIS < sixMonthsAgo
    val timeText =
        if (isOlderThanSixMonths) {
            stringResource(Res.string.unknown_age)
        } else {
            DateFormatter.formatDateTime(time * SECONDS_TO_MILLIS)
        }
    return timeText
}

fun MeshPacket.toPosition(): Position? {
    val decoded = decoded ?: return null
    return if (decoded.want_response != true) {
        decoded.payload.let { runCatching { Position.ADAPTER.decode(it) }.getOrNull() }
    } else {
        null
    }
}

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists. Only changes are included in the
 * resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
fun getChannelList(new: List<ChannelSettings>, old: List<ChannelSettings>): List<Channel> = buildList {
    for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
        if (old.getOrNull(i) != new.getOrNull(i)) {
            add(
                Channel(
                    role =
                    when (i) {
                        0 -> Channel.Role.PRIMARY
                        in 1..new.lastIndex -> Channel.Role.SECONDARY
                        else -> Channel.Role.DISABLED
                    },
                    index = i,
                    settings = new.getOrNull(i) ?: ChannelSettings(),
                ),
            )
        }
    }
}

/**
 * Builds the ADD-mode channel preview while respecting semantic duplicates and the radio's channel capacity.
 *
 * Existing channels remain first and selected. Unique incoming channels remain visible in scan order; only those that
 * fit in [maxChannels] are selected by default. Blank placeholders and channels matching an existing or earlier
 * incoming channel by effective name and PSK are omitted.
 */
fun getChannelPreviewForAdd(
    existing: List<ChannelSettings>,
    incoming: List<ChannelSettings>,
    loraConfig: Config.LoRaConfig,
    maxChannels: Int,
): ChannelAddPreview {
    val seen = existing.map { it.channelIdentity(loraConfig) }.toMutableSet()
    val previewSettings = existing.toMutableList()
    val previewSelections = MutableList(existing.size) { true }
    var remaining = (maxChannels - existing.size).coerceAtLeast(0)

    for (channel in incoming) {
        val identity = if (channel.isPlaceholder()) null else channel.channelIdentity(loraConfig)
        if (identity != null && seen.add(identity)) {
            previewSettings += channel
            val shouldSelect = remaining > 0
            previewSelections += shouldSelect
            if (shouldSelect) remaining--
        }
    }

    return ChannelAddPreview(settings = previewSettings, selections = previewSelections)
}

/** Visible ADD-mode channels paired with their size-matched default selections. */
data class ChannelAddPreview(val settings: List<ChannelSettings>, val selections: List<Boolean>)

private data class ChannelIdentity(val name: String, val psk: ByteString) {
    override fun toString(): String = "ChannelIdentity(name=$name, psk=<redacted>)"
}

private fun ChannelSettings.isPlaceholder(): Boolean = name.isBlank() && psk.size == 0

private fun ChannelSettings.channelIdentity(loraConfig: Config.LoRaConfig): ChannelIdentity {
    val channel = ModelChannel(settings = this, loraConfig = loraConfig)
    return ChannelIdentity(name = channel.name, psk = channel.psk)
}
