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
package com.ntsocial.meshlink.ios.runtime

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import com.ntsocial.meshlink.core.datastore.ChannelSetDataSource
import com.ntsocial.meshlink.core.datastore.LocalConfigDataSource
import com.ntsocial.meshlink.core.datastore.LocalStatsDataSourceImpl
import com.ntsocial.meshlink.core.datastore.ModuleConfigDataSource
import com.ntsocial.meshlink.core.datastore.RadioScopedDataSources
import com.ntsocial.meshlink.core.datastore.RadioScopedDataStoreFactory
import com.ntsocial.meshlink.core.datastore.serializer.ChannelSetSerializer
import com.ntsocial.meshlink.core.datastore.serializer.LocalConfigSerializer
import com.ntsocial.meshlink.core.datastore.serializer.LocalStatsSerializer
import com.ntsocial.meshlink.core.datastore.serializer.ModuleConfigSerializer
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.Path.Companion.toPath
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import platform.Foundation.NSLock

/** File-backed DataStores isolated by the endpoint's restart-stable catalog ID. */
internal class IosRadioScopedDataStoreFactory(private val dispatchers: CoroutineDispatchers) :
    RadioScopedDataStoreFactory {
    private val lock = NSLock()
    private val stores = mutableMapOf<RadioEndpointId, ScopedStores>()

    override fun get(endpointId: RadioEndpointId): RadioScopedDataSources = withLock {
        stores.getOrPut(endpointId) { create(endpointId) }.dataSources
    }

    override fun release(endpointId: RadioEndpointId) {
        withLock { stores.remove(endpointId) }?.scope?.cancel()
    }

    private fun create(endpointId: RadioEndpointId): ScopedStores {
        val fileStem = iosEndpointStorageFileStem(endpointId)
        val scope = CoroutineScope(dispatchers.io + SupervisorJob())
        val directory = iosDataStoreDirectory()
        val channelSetStore =
            DataStoreFactory.create(
                storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = ChannelSetSerializer,
                    producePath = { "$directory/radio_${fileStem}_channel_set.pb".toPath() },
                ),
                corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { ChannelSet() }),
                scope = scope,
            )
        val localConfigStore =
            DataStoreFactory.create(
                storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = LocalConfigSerializer,
                    producePath = { "$directory/radio_${fileStem}_local_config.pb".toPath() },
                ),
                corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalConfig() }),
                scope = scope,
            )
        val moduleConfigStore =
            DataStoreFactory.create(
                storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = ModuleConfigSerializer,
                    producePath = { "$directory/radio_${fileStem}_module_config.pb".toPath() },
                ),
                corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalModuleConfig() }),
                scope = scope,
            )
        val localStatsStore =
            DataStoreFactory.create(
                storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = LocalStatsSerializer,
                    producePath = { "$directory/radio_${fileStem}_local_stats.pb".toPath() },
                ),
                corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalStats() }),
                scope = scope,
            )
        return ScopedStores(
            dataSources =
            RadioScopedDataSources(
                channelSet = ChannelSetDataSource(channelSetStore),
                localConfig = LocalConfigDataSource(localConfigStore),
                moduleConfig = ModuleConfigDataSource(moduleConfigStore),
                localStats = LocalStatsDataSourceImpl(localStatsStore),
            ),
            scope = scope,
        )
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private data class ScopedStores(val dataSources: RadioScopedDataSources, val scope: CoroutineScope)
}
