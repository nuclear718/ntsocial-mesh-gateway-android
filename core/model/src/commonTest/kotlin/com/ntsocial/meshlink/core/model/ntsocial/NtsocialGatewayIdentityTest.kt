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
package com.ntsocial.meshlink.core.model.ntsocial

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import com.ntsocial.meshlink.core.model.Channel as ModelChannel

class NtsocialGatewayIdentityTest {
    @Test
    fun `clear fixed channel id keeps its existing identity across rename and slot reorder`() {
        val first =
            NtsocialGatewayIdentity.channel(
                Channel(
                    index = 0,
                    role = Channel.Role.PRIMARY,
                    settings = ChannelSettings(id = 0xFFFF_FF01.toInt(), name = "old"),
                ),
            )
        val reordered =
            NtsocialGatewayIdentity.channel(
                Channel(
                    index = 3,
                    role = Channel.Role.SECONDARY,
                    settings = ChannelSettings(id = 0xFFFF_FF01.toInt(), name = "renamed"),
                ),
            )

        assertEquals(first.sourceChannelId, reordered.sourceChannelId)
        assertEquals(
            "meshtastic:1269c4dd268486fadee555a320f5148d6bdb0725dffcd6f3e7673c1c446df23f",
            first.sourceChannelId,
        )
        assertEquals("CLEAR", first.securityClass)
        assertNotEquals(first.displayName, reordered.displayName)
    }

    @Test
    fun `encrypted channel identity depends only on resolved psk`() {
        val psk = ByteArray(32) { 3 }.toByteString()
        val primary =
            NtsocialGatewayIdentity.channel(
                Channel(
                    index = 0,
                    role = Channel.Role.PRIMARY,
                    settings = ChannelSettings(id = 7, name = "rescue", psk = psk),
                ),
            )
        val renamedAndReordered =
            NtsocialGatewayIdentity.channel(
                Channel(
                    index = 5,
                    role = Channel.Role.SECONDARY,
                    settings = ChannelSettings(id = 99, name = "renamed", psk = psk),
                ),
            )

        assertEquals(primary.sourceChannelId, renamedAndReordered.sourceChannelId)
        assertEquals(
            "meshtastic:c948852f19e8ccc0d59f71d94edc1fa02bdffc0a399a43dd447b5ac2d634fb4a",
            primary.sourceChannelId,
        )
        assertEquals("CUSTOM", primary.securityClass)
        assertTrue(primary.sourceChannelId.matches(Regex("meshtastic:[0-9a-f]{64}")))
        assertFalse(primary.sourceChannelId == "meshtastic:${psk.hex()}")
    }

    @Test
    fun `different resolved psks never alias even with the same numeric id`() {
        val first =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 7, name = "ops", psk = ByteArray(32) { 1 }.toByteString())),
            )
        val second =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 7, name = "ops", psk = ByteArray(32) { 2 }.toByteString())),
            )

        assertNotEquals(first.sourceChannelId, second.sourceChannelId)
    }

    @Test
    fun `well known shorthand and expanded key share identity and classification`() {
        (1..10).forEach { index ->
            val shorthand = byteArrayOf(index.toByte()).toByteString()
            val expanded = ModelChannel(ChannelSettings(psk = shorthand)).psk
            val shorthandIdentity =
                NtsocialGatewayIdentity.channel(
                    Channel(settings = ChannelSettings(id = index, name = "simple$index", psk = shorthand)),
                )
            val expandedIdentity =
                NtsocialGatewayIdentity.channel(
                    Channel(settings = ChannelSettings(id = 100 + index, name = "renamed", psk = expanded)),
                )

            assertEquals(shorthandIdentity.sourceChannelId, expandedIdentity.sourceChannelId)
            assertEquals("WELL_KNOWN", shorthandIdentity.securityClass)
            assertEquals("WELL_KNOWN", expandedIdentity.securityClass)
        }
    }

    @Test
    fun `resolved psk classification distinguishes clear and custom keys`() {
        val emptyClear = NtsocialGatewayIdentity.channel(Channel(settings = ChannelSettings(name = "clear")))
        val shorthandClear =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(name = "clear", psk = byteArrayOf(0).toByteString())),
            )
        val custom =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(name = "custom", psk = ByteArray(16) { 42 }.toByteString())),
            )
        val invalidShorthand =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(name = "custom", psk = byteArrayOf(11).toByteString())),
            )

        assertEquals("CLEAR", emptyClear.securityClass)
        assertEquals("CLEAR", shorthandClear.securityClass)
        assertEquals("CUSTOM", custom.securityClass)
        assertEquals("CUSTOM", invalidShorthand.securityClass)
    }

    @Test
    fun `clear zero id keeps its existing resolved name fallback`() {
        val longFast = LoRaConfig(use_preset = true, modem_preset = ModemPreset.LONG_FAST)
        val blankName =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 0, name = "")),
                loraConfig = longFast,
            )
        val normalizedName =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 0, name = " longfast ")),
                loraConfig = longFast,
            )

        assertEquals(blankName.sourceChannelId, normalizedName.sourceChannelId)
        assertEquals(
            "meshtastic:a8b0dcf7f9b36ba34e75187fccb820dc8d2d6ec4939f63023ed83769fb07d4ea",
            blankName.sourceChannelId,
        )
    }
}
