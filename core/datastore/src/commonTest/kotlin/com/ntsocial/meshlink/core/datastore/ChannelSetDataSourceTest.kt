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
package com.ntsocial.meshlink.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelSetDataSourceTest {
    @Test
    fun `replaceChannelSet replaces settings and LoRa config together`() = runTest {
        val old = ChannelSet(settings = listOf(ChannelSettings(name = "old")))
        val store = InMemoryChannelSetDataStore(old)
        val dataSource = ChannelSetDataSource(store)
        val replacement =
            ChannelSet(
                settings = listOf(ChannelSettings(name = "primary"), ChannelSettings(name = "secondary")),
                lora_config = Config.LoRaConfig(use_preset = true),
            )

        dataSource.replaceChannelSet(replacement)

        assertEquals(replacement, dataSource.channelSetFlow.first())
    }

    @Test
    fun `disabled trailing channel removes stale observed slot`() = runTest {
        val primary = ChannelSettings(name = "primary")
        val store =
            InMemoryChannelSetDataStore(ChannelSet(settings = listOf(primary, ChannelSettings(name = "secondary"))))
        val dataSource = ChannelSetDataSource(store)

        dataSource.updateChannelSettings(Channel(index = 1, role = Channel.Role.DISABLED))

        assertEquals(listOf(primary), dataSource.channelSetFlow.first().settings)
    }

    @Test
    fun `disabled interior channel preserves later slot index`() = runTest {
        val primary = ChannelSettings(name = "primary")
        val last = ChannelSettings(name = "last")
        val store =
            InMemoryChannelSetDataStore(ChannelSet(settings = listOf(primary, ChannelSettings(name = "removed"), last)))
        val dataSource = ChannelSetDataSource(store)

        dataSource.updateChannelSettings(Channel(index = 1, role = Channel.Role.DISABLED))

        assertEquals(listOf(primary, ChannelSettings(), last), dataSource.channelSetFlow.first().settings)
    }
}

private class InMemoryChannelSetDataStore(initial: ChannelSet = ChannelSet()) : DataStore<ChannelSet> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)

    override val data: Flow<ChannelSet> = state

    override suspend fun updateData(transform: suspend (t: ChannelSet) -> ChannelSet): ChannelSet =
        mutex.withLock { transform(state.value).also { state.value = it } }
}
