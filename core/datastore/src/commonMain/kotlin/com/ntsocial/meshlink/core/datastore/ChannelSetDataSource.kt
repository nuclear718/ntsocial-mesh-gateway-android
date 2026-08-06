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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import okio.IOException
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/** Class that handles saving and retrieving [ChannelSet] data. */
@Single
class ChannelSetDataSource(@Named("CoreChannelSetDataStore") private val channelSetStore: DataStore<ChannelSet>) {
    val channelSetFlow: Flow<ChannelSet> =
        channelSetStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Logger.e { "Error reading DeviceConfig settings: ${exception.message}" }
                emit(ChannelSet())
            } else {
                throw exception
            }
        }

    suspend fun clearChannelSet() {
        channelSetStore.updateData { ChannelSet() }
    }

    /** Replaces all [ChannelSettings] in a single atomic operation. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetStore.updateData { it.copy(settings = settingsList) }
    }

    /** Replaces the complete radio-observed [ChannelSet] in one DataStore transaction. */
    suspend fun replaceChannelSet(channelSet: ChannelSet) {
        channelSetStore.updateData { channelSet }
    }

    /** Updates the [ChannelSettings] list with the provided channel. */
    suspend fun updateChannelSettings(channel: Channel) {
        channelSetStore.updateData { preference ->
            val settings = preference.settings.toMutableList()
            if (channel.role == Channel.Role.DISABLED) {
                if (channel.index in settings.indices) {
                    settings[channel.index] = ChannelSettings()
                    while (settings.size > 1 && settings.last() == ChannelSettings()) settings.removeLast()
                }
            } else {
                // Resize to fit channel.
                while (settings.size <= channel.index) settings.add(ChannelSettings())
                // Use indexed settings so the persisted list and firmware slots stay aligned.
                settings[channel.index] = channel.settings ?: ChannelSettings()
            }
            preference.copy(settings = settings)
        }
    }

    suspend fun setLoraConfig(config: Config.LoRaConfig) {
        channelSetStore.updateData { it.copy(lora_config = config) }
    }
}
