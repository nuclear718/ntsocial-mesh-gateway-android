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

import okio.ByteString.Companion.decodeHex
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelSnapshotDriftTest {
    private val lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW)
    private val primary = channel("Primary", "01")
    private val secondaryOne = channel("Secondary 1", "02")
    private val secondaryTwo = channel("Secondary 2", "03")
    private val snapshot = channelSet(primary, secondaryOne, secondaryTwo)

    @Test
    fun `identical complete readback is exact`() {
        val result = classify(current = snapshot)

        assertEquals(ChannelSnapshotDrift.EXACT, result)
    }

    @Test
    fun `disabled trailing readback slots do not count as new channels`() {
        val current = snapshot.copy(settings = snapshot.settings + List(5) { ChannelSettings() })

        assertEquals(ChannelSnapshotDrift.EXACT, classify(current = current))
    }

    @Test
    fun `missing trailing secondary is the only repairable drift`() {
        val current = channelSet(primary, secondaryOne)

        assertEquals(ChannelSnapshotDrift.MISSING_SECONDARY_ONLY, classify(current = current))
    }

    @Test
    fun `missing interior secondary is the only repairable drift`() {
        val current = channelSet(primary, ChannelSettings(), secondaryTwo)

        assertEquals(ChannelSnapshotDrift.MISSING_SECONDARY_ONLY, classify(current = current))
    }

    @Test
    fun `multiple missing secondaries are repairable when nothing else changed`() {
        val current = channelSet(primary)

        assertEquals(ChannelSnapshotDrift.MISSING_SECONDARY_ONLY, classify(current = current))
    }

    @Test
    fun `missing primary is a conflict`() {
        val current = channelSet(ChannelSettings(), secondaryOne, secondaryTwo)

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    @Test
    fun `changed primary is a conflict`() {
        val current = channelSet(channel("Other primary", "04"), secondaryOne, secondaryTwo)

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    @Test
    fun `changed secondary slot is a conflict`() {
        val current = channelSet(primary, channel("Changed", "05"), secondaryTwo)

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    @Test
    fun `new current channel is a conflict`() {
        val current = channelSet(primary, secondaryOne, secondaryTwo, channel("New", "06"))

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    @Test
    fun `new channel plus a missing protected secondary remains a conflict`() {
        val current = channelSet(primary, ChannelSettings(), secondaryTwo, channel("New", "06"))

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    @Test
    fun `LoRa change is a conflict`() {
        val current = snapshot.copy(lora_config = lora.copy(region = Config.LoRaConfig.RegionCode.US))

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    @Test
    fun `radio capacity change is a conflict`() {
        val result = classify(current = snapshot, currentMaxChannels = 6)

        assertEquals(ChannelSnapshotDrift.CONFLICT, result)
    }

    @Test
    fun `unknown radio capacity is a conflict`() {
        val result = classify(current = snapshot, currentMaxChannels = 0)

        assertEquals(ChannelSnapshotDrift.CONFLICT, result)
    }

    @Test
    fun `snapshot without a primary is a conflict`() {
        val emptySnapshot = ChannelSet(lora_config = lora)

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(snapshot = emptySnapshot, current = snapshot))
    }

    @Test
    fun `snapshot with a placeholder secondary is a conflict`() {
        val malformedSnapshot = channelSet(primary, ChannelSettings(), secondaryTwo)

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(snapshot = malformedSnapshot, current = snapshot))
    }

    @Test
    fun `readback larger than current capacity is a conflict even when excess slots are disabled`() {
        val current = snapshot.copy(settings = snapshot.settings + List(6) { ChannelSettings() })

        assertEquals(ChannelSnapshotDrift.CONFLICT, classify(current = current))
    }

    private fun classify(
        snapshot: ChannelSet = this.snapshot,
        snapshotMaxChannels: Int = 8,
        current: ChannelSet,
        currentMaxChannels: Int = 8,
    ): ChannelSnapshotDrift = classifyChannelSnapshotDrift(
        snapshotChannelSet = snapshot,
        snapshotMaxChannels = snapshotMaxChannels,
        currentChannelSet = current,
        currentMaxChannels = currentMaxChannels,
    )

    private fun channelSet(vararg settings: ChannelSettings): ChannelSet =
        ChannelSet(settings = settings.toList(), lora_config = lora)

    private fun channel(name: String, pskHex: String): ChannelSettings =
        ChannelSettings(name = name, psk = pskHex.decodeHex())
}
