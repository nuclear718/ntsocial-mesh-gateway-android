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
package com.ntsocial.meshlink.core.data.repository

import androidx.datastore.core.DataStore
import com.ntsocial.meshlink.core.datastore.ChannelSetDataSource
import com.ntsocial.meshlink.core.datastore.LocalConfigDataSource
import com.ntsocial.meshlink.core.datastore.ModuleConfigDataSource
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.repository.NodeRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
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
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioConfigRepositoryImplTest {
    private val nodeRepository = mock<NodeRepository>(MockMode.autofill)

    private lateinit var repository: RadioConfigRepositoryImpl
    private lateinit var channelSetStore: InMemoryDataStore<ChannelSet>
    private lateinit var localConfigStore: InMemoryDataStore<LocalConfig>

    @BeforeTest
    fun setUp() {
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow<Node?>(null)
        channelSetStore = InMemoryDataStore(ChannelSet())
        localConfigStore = InMemoryDataStore(LocalConfig())
        repository =
            RadioConfigRepositoryImpl(
                nodeDB = nodeRepository,
                channelSetDataSource = ChannelSetDataSource(channelSetStore),
                localConfigDataSource = LocalConfigDataSource(localConfigStore),
                moduleConfigDataSource = ModuleConfigDataSource(InMemoryDataStore(LocalModuleConfig())),
            )
    }

    @Test
    fun `complete handshake replacement is the only write that advances readback generation`() = runTest {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "primary")))

        repository.replaceChannelSet(channelSet)
        assertEquals(2L, repository.channelSnapshotGeneration.value)
        repository.updateChannelSettings(Channel(index = 0, settings = channelSet.settings.single()))
        assertEquals(4L, repository.channelSnapshotGeneration.value)
        assertEquals(0L, repository.channelReadbackGeneration.value)

        repository.replaceChannelSet(channelSet, completeReadback = true)

        assertEquals(channelSet, channelSetStore.data.first())
        assertEquals(1L, repository.channelReadbackGeneration.value)
        assertEquals(6L, repository.channelSnapshotGeneration.value)
    }

    @Test
    fun `handshake config persistence does not mutate ChannelSet independently`() = runTest {
        val config = Config(lora = Config.LoRaConfig(use_preset = true))

        repository.setLocalConfigFromHandshake(config)

        assertEquals(config.lora, localConfigStore.data.first().lora)
        assertEquals(null, channelSetStore.data.first().lora_config)
    }
}

private class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)

    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T =
        mutex.withLock { transform(state.value).also { state.value = it } }
}
