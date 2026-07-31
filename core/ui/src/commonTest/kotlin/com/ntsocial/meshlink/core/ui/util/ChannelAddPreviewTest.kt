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

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelAddPreviewTest {

    @Test
    fun eightChannelQrWithExistingPrimarySelectsOnlyRadioCapacity() {
        val existing = listOf(channel("Existing", 1))
        val incoming = (1..8).map { channel("Incoming $it", it + 1) }

        val preview =
            getChannelPreviewForAdd(existing = existing, incoming = incoming, loraConfig = LORA_CONFIG, maxChannels = 8)

        assertEquals(9, preview.settings.size)
        assertEquals(preview.settings.size, preview.selections.size)
        assertEquals(8, preview.selections.count { it })
        assertTrue(preview.selections.take(8).all { it })
        assertFalse(preview.selections.last())
    }

    @Test
    fun duplicateAndPlaceholderDoNotConsumeAvailableSlot() {
        val existingChannel = channel("Existing", 1)
        val uniqueChannel = channel("Unique", 2)
        val overflowChannel = channel("Overflow", 3)

        val preview =
            getChannelPreviewForAdd(
                existing = listOf(existingChannel),
                incoming = listOf(existingChannel, ChannelSettings(), uniqueChannel, overflowChannel),
                loraConfig = LORA_CONFIG,
                maxChannels = 2,
            )

        assertEquals(listOf(existingChannel, uniqueChannel, overflowChannel), preview.settings)
        assertEquals(listOf(true, true, false), preview.selections)
    }

    @Test
    fun fullRadioKeepsUniqueIncomingChannelVisibleButUnchecked() {
        val preview =
            getChannelPreviewForAdd(
                existing = listOf(channel("Primary", 1), channel("Secondary", 2)),
                incoming = listOf(channel("Incoming", 3)),
                loraConfig = LORA_CONFIG,
                maxChannels = 2,
            )

        assertEquals(3, preview.settings.size)
        assertEquals(listOf(true, true, false), preview.selections)
    }

    private fun channel(name: String, keyByte: Int): ChannelSettings =
        ChannelSettings(name = name, psk = byteArrayOf(keyByte.toByte(), (keyByte + 1).toByte()).toByteString())

    private companion object {
        val LORA_CONFIG = Config.LoRaConfig(use_preset = true)
    }
}
