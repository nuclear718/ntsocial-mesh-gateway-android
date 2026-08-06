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
package com.ntsocial.meshlink.core.repository

import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings

/** A user-approved channel configuration that may be compared with later radio readbacks. */
data class ChannelProtectionSnapshot(val maxChannels: Int, val channelSet: ChannelSet) {
    init {
        require(maxChannels > 0) { "maxChannels must be positive" }
        require(channelSet.settings.isNotEmpty()) { "A protected snapshot must contain a primary channel" }
        require(channelSet.settings.first() != ChannelSettings()) {
            "A protected snapshot must contain a valid primary"
        }
        require(channelSet.settings.size <= maxChannels) { "A protected snapshot exceeds the radio channel capacity" }
    }

    /** Avoids exposing channel names or PSKs if a caller accidentally logs this value. */
    override fun toString(): String =
        "ChannelProtectionSnapshot(maxChannels=$maxChannels, channelCount=${channelSet.settings.size}, " +
            "hasLoraConfig=${channelSet.lora_config != null})"
}

/** App-private persistence for user-approved channel snapshots, partitioned by stable radio identity. */
interface ChannelSnapshotRepository {
    /** Returns the protected snapshot for [stableDeviceIdentity], or null when protection has never been enabled. */
    suspend fun get(stableDeviceIdentity: String): ChannelProtectionSnapshot?

    /** Atomically stores [snapshot] for [stableDeviceIdentity]. */
    suspend fun save(stableDeviceIdentity: String, snapshot: ChannelProtectionSnapshot)

    /** Removes any protected snapshot for [stableDeviceIdentity]. */
    suspend fun clear(stableDeviceIdentity: String)
}
