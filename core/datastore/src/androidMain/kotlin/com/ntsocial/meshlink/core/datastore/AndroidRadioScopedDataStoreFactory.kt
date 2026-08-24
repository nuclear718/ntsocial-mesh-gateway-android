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

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.dataStoreFile
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
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Single
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

@Single(binds = [RadioScopedDataStoreFactory::class])
class AndroidRadioScopedDataStoreFactory(private val context: Context, private val dispatchers: CoroutineDispatchers) :
    RadioScopedDataStoreFactory {
    private val lock = Any()
    private val stores = mutableMapOf<RadioEndpointId, ScopedStores>()

    override fun get(endpointId: RadioEndpointId): RadioScopedDataSources {
        stores[endpointId]?.let {
            return it.dataSources
        }
        return synchronized(lock) { stores.getOrPut(endpointId) { create(endpointId) }.dataSources }
    }

    override fun release(endpointId: RadioEndpointId) {
        synchronized(lock) { stores.remove(endpointId) }?.scope?.cancel()
    }

    private fun create(endpointId: RadioEndpointId): ScopedStores {
        val fileStem = endpointId.value.filter(Char::isLetterOrDigit).take(FILE_STEM_LENGTH)
        require(fileStem.isNotBlank()) { "Endpoint ID cannot produce an empty DataStore file name" }
        val scope = CoroutineScope(dispatchers.io + SupervisorJob())
        val channelSetStore =
            DataStoreFactory.create(
                storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = ChannelSetSerializer,
                    producePath = { context.dataStoreFile("radio_${fileStem}_channel_set.pb").toOkioPath() },
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
                    producePath = { context.dataStoreFile("radio_${fileStem}_local_config.pb").toOkioPath() },
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
                    producePath = { context.dataStoreFile("radio_${fileStem}_module_config.pb").toOkioPath() },
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
                    producePath = { context.dataStoreFile("radio_${fileStem}_local_stats.pb").toOkioPath() },
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

    private data class ScopedStores(val dataSources: RadioScopedDataSources, val scope: CoroutineScope)

    private companion object {
        const val FILE_STEM_LENGTH = 32
    }
}
