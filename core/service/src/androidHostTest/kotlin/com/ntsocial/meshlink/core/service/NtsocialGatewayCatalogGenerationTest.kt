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
package com.ntsocial.meshlink.core.service

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NtsocialGatewayCatalogGenerationTest {
    @Test
    fun `generation is opaque stable for repeats and rotates for config change or restart`() {
        val tokens = ArrayDeque(listOf("runtime-1", "runtime-2", "runtime-3", "runtime-4", "runtime-5"))
        val tracker = GatewayCatalogGenerationTracker(tokens::removeFirst)
        val custom =
            ChannelSet(settings = listOf(ChannelSettings(name = "ops", psk = ByteArray(32) { 7 }.toByteString())))

        val first = tracker.update(custom)
        assertEquals(first, tracker.update(custom))

        val changed = tracker.update(custom.copy(settings = custom.settings.map { it.copy(name = "renamed") }))
        assertNotEquals(first, changed)

        val restarted = GatewayCatalogGenerationTracker(tokens::removeFirst).update(custom)
        assertNotEquals(first, restarted)
    }
}
