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
package com.ntsocial.meshlink.core.prefs.channel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.ChannelSet

/** Stores protected channel snapshots in the existing app-private UI preferences DataStore. */
@Single
class ChannelSnapshotRepositoryImpl(@Named("UiDataStore") private val dataStore: DataStore<Preferences>) :
    ChannelSnapshotRepository {
    override suspend fun get(stableDeviceIdentity: String): ChannelProtectionSnapshot? {
        if (stableDeviceIdentity.isBlank()) return null

        val keys = snapshotKeys(stableDeviceIdentity)
        val preferences = dataStore.data.first()
        val encodedChannelSet = preferences[keys.channelSet]
        val maxChannels = preferences[keys.maxChannels]
        return if (encodedChannelSet == null || maxChannels == null) {
            null
        } else {
            decodeChannelSet(encodedChannelSet)?.let { channelSet ->
                runCatching { ChannelProtectionSnapshot(maxChannels = maxChannels, channelSet = channelSet) }
                    .getOrNull()
            }
        }
    }

    override suspend fun save(stableDeviceIdentity: String, snapshot: ChannelProtectionSnapshot) {
        require(stableDeviceIdentity.isNotBlank()) { "A stable device identity is required" }

        val keys = snapshotKeys(stableDeviceIdentity)
        val encodedChannelSet = ChannelSet.ADAPTER.encode(snapshot.channelSet).toByteString().base64()
        dataStore.edit { preferences ->
            preferences[keys.channelSet] = encodedChannelSet
            preferences[keys.maxChannels] = snapshot.maxChannels
        }
    }

    override suspend fun clear(stableDeviceIdentity: String) {
        if (stableDeviceIdentity.isBlank()) return

        val keys = snapshotKeys(stableDeviceIdentity)
        dataStore.edit { preferences ->
            preferences.remove(keys.channelSet)
            preferences.remove(keys.maxChannels)
        }
    }

    private fun decodeChannelSet(encoded: String): ChannelSet? = runCatching {
        val bytes = encoded.decodeBase64() ?: return null
        ChannelSet.ADAPTER.decode(bytes)
    }
        .getOrNull()
}

private const val SNAPSHOT_KEY_DOMAIN = "com.ntsocial.meshlink.channel-protection.v1\u0000"
private const val CHANNEL_SET_KEY_PREFIX = "channel-protection-set-v1-"
private const val MAX_CHANNELS_KEY_PREFIX = "channel-protection-max-v1-"

private data class SnapshotKeys(val channelSet: Preferences.Key<String>, val maxChannels: Preferences.Key<Int>)

private fun snapshotKeys(stableDeviceIdentity: String): SnapshotKeys {
    val identityHash = (SNAPSHOT_KEY_DOMAIN + stableDeviceIdentity).encodeUtf8().sha256().hex()
    return SnapshotKeys(
        channelSet = stringPreferencesKey(CHANNEL_SET_KEY_PREFIX + identityHash),
        maxChannels = intPreferencesKey(MAX_CHANNELS_KEY_PREFIX + identityHash),
    )
}
