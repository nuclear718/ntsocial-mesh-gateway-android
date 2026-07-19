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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ntsocial.meshlink.core.model.util.CHANNEL_URL_PREFIX
import com.ntsocial.meshlink.core.model.util.getChannelUrl
import com.ntsocial.meshlink.core.model.util.toChannelSet
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

@RunWith(AndroidJUnit4::class)
class ChannelTest {
    @Test
    fun channelUrlGood() {
        val ch = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig)
        val channelUrl = ch.getChannelUrl()

        Assert.assertTrue(channelUrl.toString().startsWith(CHANNEL_URL_PREFIX))
        Assert.assertEquals(channelUrl.toChannelSet(), ch)
    }

    @Test
    fun channelHashGood() {
        val ch = Channel.default

        Assert.assertEquals(8, ch.hash)
    }

    @Test
    fun numChannelsGood() {
        val ch = Channel.default

        Assert.assertEquals(104, ch.loraConfig.numChannels)
    }

    @Test
    fun channelNumGood() {
        val ch = Channel.default

        Assert.assertEquals(20, ch.channelNum)
    }

    @Test
    fun radioFreqGood() {
        val ch = Channel.default

        Assert.assertEquals(906.875f, ch.radioFreq)
    }

    @Test
    fun allModemPresetsHaveValidNames() {
        Config.LoRaConfig.ModemPreset.entries.forEach { preset ->
            // Skip UNRECOGNIZED if it exists (Wire generates it sometimes) or generic UNSET values if applicable
            if (preset.name == "UNSET" || preset.name == "UNRECOGNIZED") return@forEach

            val loraConfig = Channel.default.loraConfig.copy(use_preset = true, modem_preset = preset)
            val channel = Channel(loraConfig = loraConfig)

            // We want to ensure it is NOT "Invalid"
            Assert.assertNotEquals("Preset ${preset.name} should typically have a valid name", "Invalid", channel.name)
        }
    }
}
