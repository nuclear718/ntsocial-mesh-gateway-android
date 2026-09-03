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
package com.ntsocial.meshlink.core.model.util

import com.ntsocial.meshlink.core.common.util.CommonUri
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelSetUrlTest {

    @Test
    fun parsesDenseOfficialAddUrlWithEightChannels() {
        val original =
            ChannelSet(
                settings =
                (1..8).map { index ->
                    ChannelSettings(
                        name = "Channel $index",
                        psk = ByteArray(32) { offset -> (index * 17 + offset).toByte() }.toByteString(),
                        uplink_enabled = true,
                        downlink_enabled = true,
                    )
                },
                lora_config =
                Config.LoRaConfig(
                    use_preset = true,
                    modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST,
                    hop_limit = 3,
                    tx_enabled = true,
                ),
            )

        val url = original.getChannelUrl(shouldAdd = true)
        val parsed = url.toChannelSet()

        assertTrue(url.toString().startsWith("https://meshtastic.org/e/?add=true#"))
        assertTrue(url.toString().length > 500)
        assertEquals(original.settings, parsed.settings)
        assertNull(parsed.lora_config)
    }

    @Test
    fun malformedChannelUrlExceptionDoesNotExposeScannedContents() {
        val secret = "otpauth-secret-value"

        val exception =
            assertFailsWith<MalformedMeshtasticUrlException> {
                CommonUri.parse("https://example.invalid/e/#$secret").toChannelSet()
            }

        assertFalse(exception.message.orEmpty().contains(secret))
        assertFalse(exception.message.orEmpty().contains("example.invalid"))
    }

    @Test
    fun invalidChannelPayloadExceptionDoesNotExposeFragment() {
        val secret = "private-channel-psk!"

        val exception =
            assertFailsWith<MalformedMeshtasticUrlException> {
                CommonUri.parse("https://meshtastic.org/e/#$secret").toChannelSet()
            }

        assertFalse(exception.message.orEmpty().contains(secret))
    }
}
