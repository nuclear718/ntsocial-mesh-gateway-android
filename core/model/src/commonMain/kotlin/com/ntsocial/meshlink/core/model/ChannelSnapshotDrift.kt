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

import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings

/** Conservative comparison result between a protected channel snapshot and a complete radio readback. */
enum class ChannelSnapshotDrift {
    /** The readback has the same capacity, LoRa configuration, primary, and secondary channel slots. */
    EXACT,

    /** One or more protected secondary slots are absent, with no other observed difference. */
    MISSING_SECONDARY_ONLY,

    /** The readback is unsafe to repair automatically. */
    CONFLICT,
}

/**
 * Classifies drift without guessing user intent.
 *
 * Only absent protected secondary channels are repairable. A primary difference, LoRa difference, capacity change,
 * changed slot, newly observed channel, or malformed snapshot is a [ChannelSnapshotDrift.CONFLICT]. Empty trailing
 * settings in [currentChannelSet] are treated as disabled slots rather than newly observed channels.
 */
fun classifyChannelSnapshotDrift(
    snapshotChannelSet: ChannelSet,
    snapshotMaxChannels: Int,
    currentChannelSet: ChannelSet,
    currentMaxChannels: Int,
): ChannelSnapshotDrift {
    val snapshotSettings = snapshotChannelSet.settings
    val currentSettings = currentChannelSet.settings
    return when {
        !hasCompatibleShape(snapshotSettings, snapshotMaxChannels, currentSettings, currentMaxChannels) ->
            ChannelSnapshotDrift.CONFLICT

        snapshotChannelSet.lora_config != currentChannelSet.lora_config -> ChannelSnapshotDrift.CONFLICT

        currentSettings.firstOrNull() != snapshotSettings.first() -> ChannelSnapshotDrift.CONFLICT

        else -> classifySecondaryDrift(snapshotSettings, currentSettings)
    }
}

private fun hasCompatibleShape(
    snapshotSettings: List<ChannelSettings>,
    snapshotMaxChannels: Int,
    currentSettings: List<ChannelSettings>,
    currentMaxChannels: Int,
): Boolean {
    val capacitiesValid = snapshotMaxChannels > 0 && currentMaxChannels > 0
    val capacitiesMatch = snapshotMaxChannels == currentMaxChannels
    val snapshotShapeValid =
        snapshotSettings.isNotEmpty() &&
            snapshotSettings.size <= snapshotMaxChannels &&
            snapshotSettings.drop(1).none(ChannelSettings::isDisabledPlaceholder)
    val currentShapeValid = currentSettings.size <= currentMaxChannels
    return capacitiesValid && capacitiesMatch && snapshotShapeValid && currentShapeValid
}

private fun classifySecondaryDrift(
    snapshotSettings: List<ChannelSettings>,
    currentSettings: List<ChannelSettings>,
): ChannelSnapshotDrift {
    var missingSecondary = false
    var conflictingSlot = false
    for (index in 1..snapshotSettings.lastIndex) {
        val expected = snapshotSettings[index]
        val current = currentSettings.getOrNull(index)
        when {
            current == expected -> Unit
            current == null || current.isDisabledPlaceholder() -> missingSecondary = true
            else -> conflictingSlot = true
        }
    }

    val hasNewCurrentChannel =
        currentSettings.drop(snapshotSettings.size).any { current -> !current.isDisabledPlaceholder() }
    return when {
        conflictingSlot || hasNewCurrentChannel -> ChannelSnapshotDrift.CONFLICT
        missingSecondary -> ChannelSnapshotDrift.MISSING_SECONDARY_ONLY
        else -> ChannelSnapshotDrift.EXACT
    }
}

private fun ChannelSettings.isDisabledPlaceholder(): Boolean = this == ChannelSettings()
