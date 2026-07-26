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
import kotlin.test.assertNotEquals

class NtsocialGatewayIdentityTest {
    @Test
    fun `fixed channel id survives rename and slot reorder`() {
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
        assertNotEquals(first.displayName, reordered.displayName)
    }

    @Test
    fun `legacy zero id includes name and resolved key`() {
        val psk = byteArrayOf(1).toByteString()
        val alpha =
            NtsocialGatewayIdentity.channel(Channel(settings = ChannelSettings(id = 0, name = "alpha", psk = psk)))
        val beta =
            NtsocialGatewayIdentity.channel(Channel(settings = ChannelSettings(id = 0, name = "beta", psk = psk)))

        assertNotEquals(alpha.sourceChannelId, beta.sourceChannelId)
        assertEquals("WELL_KNOWN", alpha.securityClass)
    }

    @Test
    fun `public zero id is cross install stable while custom zero id is install local`() {
        val installA = ByteArray(32) { 1 }.toByteString()
        val installB = ByteArray(32) { 2 }.toByteString()
        val publicSettings = ChannelSettings(name = "LongFast", psk = byteArrayOf(1).toByteString())
        val customSettings = ChannelSettings(name = "rescue", psk = ByteArray(32) { 3 }.toByteString())

        val publicA =
            NtsocialGatewayIdentity.channel(Channel(settings = publicSettings), legacyChannelHmacKey = installA)
        val publicB =
            NtsocialGatewayIdentity.channel(Channel(settings = publicSettings), legacyChannelHmacKey = installB)
        val customA =
            NtsocialGatewayIdentity.channel(Channel(settings = customSettings), legacyChannelHmacKey = installA)
        val customARepeat =
            NtsocialGatewayIdentity.channel(Channel(settings = customSettings), legacyChannelHmacKey = installA)
        val customB =
            NtsocialGatewayIdentity.channel(Channel(settings = customSettings), legacyChannelHmacKey = installB)

        assertEquals(publicA.sourceChannelId, publicB.sourceChannelId)
        assertEquals(customA.sourceChannelId, customARepeat.sourceChannelId)
        assertNotEquals(customA.sourceChannelId, customB.sourceChannelId)
    }

    @Test
    fun `public zero id resolves blank default name and normalizes across installs`() {
        val installA = ByteArray(32) { 1 }.toByteString()
        val installB = ByteArray(32) { 2 }.toByteString()
        val psk = byteArrayOf(1).toByteString()
        val longFast = LoRaConfig(use_preset = true, modem_preset = ModemPreset.LONG_FAST)
        val blankName =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 0, name = "", psk = psk)),
                loraConfig = longFast,
                legacyChannelHmacKey = installA,
            )
        val explicitResolvedName =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 0, name = "LongFast", psk = psk)),
                loraConfig = longFast,
                legacyChannelHmacKey = installB,
            )
        val normalizedName =
            NtsocialGatewayIdentity.channel(
                Channel(settings = ChannelSettings(id = 0, name = " longfast ", psk = psk)),
                loraConfig = longFast,
                legacyChannelHmacKey = installB,
            )

        assertEquals(blankName.sourceChannelId, explicitResolvedName.sourceChannelId)
        assertEquals(blankName.sourceChannelId, normalizedName.sourceChannelId)
    }
}
