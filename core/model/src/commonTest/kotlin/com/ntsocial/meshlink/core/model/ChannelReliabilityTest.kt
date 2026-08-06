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
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelReliabilityTest {
    private val lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW)
    private val primary = channel("Primary", "01")
    private val secondary = channel("Secondary", "02")
    private val otherSecondary = channel("Other", "03")

    @Test
    fun `normalization removes placeholders and semantic duplicates without changing primary`() {
        val result =
            normalizeReliableChannelSettings(
                settings = listOf(primary, ChannelSettings(), secondary, secondary, otherSecondary),
                loraConfig = lora,
            )

        assertEquals(listOf(primary, secondary, otherSecondary), result)
    }

    @Test
    fun `authoritative replacement writes every slot and disables the trailing slots`() {
        val writes = buildAuthoritativeChannelWrites(listOf(primary, secondary), maxChannels = 4)

        assertEquals(listOf(0, 1, 2, 3), writes.map(Channel::index))
        assertEquals(
            listOf(Channel.Role.PRIMARY, Channel.Role.SECONDARY, Channel.Role.DISABLED, Channel.Role.DISABLED),
            writes.map(Channel::role),
        )
        assertEquals(ChannelSettings(), writes[2].settings)
        assertEquals(ChannelSettings(), writes[3].settings)
    }

    @Test
    fun `missing repair writes only absent protected secondary slots`() {
        val writes =
            buildMissingSecondaryWrites(
                protectedSettings = listOf(primary, secondary, otherSecondary),
                currentSettings = listOf(primary, ChannelSettings(), otherSecondary),
            )

        assertEquals(1, writes.size)
        assertEquals(1, writes.single().index)
        assertEquals(Channel.Role.SECONDARY, writes.single().role)
        assertEquals(secondary, writes.single().settings)
    }

    private fun channel(name: String, pskHex: String): ChannelSettings =
        ChannelSettings(name = name, psk = pskHex.decodeHex())
}
