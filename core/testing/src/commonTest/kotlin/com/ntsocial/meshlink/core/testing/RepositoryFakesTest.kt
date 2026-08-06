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
package com.ntsocial.meshlink.core.testing

import com.ntsocial.meshlink.core.database.entity.FirmwareRelease
import com.ntsocial.meshlink.core.database.entity.QuickChatAction
import com.ntsocial.meshlink.core.model.DeviceHardware
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryFakesTest {

    @Test
    fun `FakeDeviceHardwareRepository returns seeded hardware and records calls`() = runTest {
        val repo = FakeDeviceHardwareRepository()
        val hw = DeviceHardware(hwModel = 42, hwModelSlug = "TEST", platformioTarget = "tlora")
        repo.setHardware(hwModel = 42, target = "tlora", device = hw)

        val hit = repo.getDeviceHardwareByModel(hwModel = 42, target = "tlora", forceRefresh = false)
        val miss = repo.getDeviceHardwareByModel(hwModel = 99)

        assertEquals(hw, hit.getOrNull())
        assertNull(miss.getOrNull())
        assertEquals(2, repo.recordedCalls.size)
        assertEquals(Triple(42, "tlora", false), repo.recordedCalls.first())
    }

    @Test
    fun `FakeFirmwareReleaseRepository emits stable and alpha releases`() = runTest {
        val repo = FakeFirmwareReleaseRepository()
        val stable = FirmwareRelease(id = "1.0", title = "1.0", pageUrl = "", zipUrl = "")
        val alpha = FirmwareRelease(id = "1.1-a", title = "1.1-a", pageUrl = "", zipUrl = "")

        repo.setStableRelease(stable)
        repo.setAlphaRelease(alpha)

        assertEquals(stable, repo.stableRelease.first())
        assertEquals(alpha, repo.alphaRelease.first())

        repo.invalidateCache()
        repo.invalidateCache()
        assertEquals(2, repo.invalidateCacheCalls)
    }

    @Test
    fun `FakeQuickChatActionRepository upsert delete and reorder`() = runTest {
        val repo = FakeQuickChatActionRepository()
        val a = QuickChatAction(uuid = 1L, name = "A", message = "hi", position = 0)
        val b = QuickChatAction(uuid = 2L, name = "B", message = "bye", position = 1)

        repo.upsert(a)
        repo.upsert(b)
        assertEquals(listOf(a, b), repo.getAllActions().first())

        repo.setItemPosition(uuid = 1L, newPos = 5)
        assertEquals(listOf(2L, 1L), repo.getAllActions().first().map { it.uuid })

        repo.delete(b)
        assertEquals(1, repo.currentActions.size)

        repo.deleteAll()
        assertTrue(repo.currentActions.isEmpty())
    }

    @Test
    fun `FakeQuickChatActionRepository delete compacts positions`() = runTest {
        val repo = FakeQuickChatActionRepository()
        val a = QuickChatAction(uuid = 1L, name = "A", message = "", position = 0)
        val b = QuickChatAction(uuid = 2L, name = "B", message = "", position = 1)
        val c = QuickChatAction(uuid = 3L, name = "C", message = "", position = 2)
        repo.upsert(a)
        repo.upsert(b)
        repo.upsert(c)

        repo.delete(b)

        // Matches real DAO's decrementPositionsAfter: positions must stay contiguous.
        assertEquals(listOf(1L to 0, 3L to 1), repo.currentActions.map { it.uuid to it.position })
    }

    @Test
    fun `FakeRadioConfigRepository tracks channel set and module config`() = runTest {
        val repo = FakeRadioConfigRepository()
        val a = ChannelSettings(name = "A")
        val b = ChannelSettings(name = "B")

        repo.replaceChannelSet(ChannelSet(settings = listOf(a, b)))
        assertEquals(listOf(a, b), repo.currentChannelSet.settings)

        repo.updateChannelSettings(Channel(index = 1, settings = ChannelSettings(name = "B2")))
        assertEquals("B2", repo.currentChannelSet.settings[1].name)

        repo.replaceChannelSet(ChannelSet(settings = emptyList()))
        assertTrue(repo.currentChannelSet.settings.isEmpty())
    }
}
