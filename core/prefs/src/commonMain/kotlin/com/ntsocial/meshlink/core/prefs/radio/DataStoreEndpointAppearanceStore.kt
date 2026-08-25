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
package com.ntsocial.meshlink.core.prefs.radio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearance
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearanceStore
import com.ntsocial.meshlink.core.radiofleet.conversation.NodeAccentToken
import com.ntsocial.meshlink.core.radiofleet.conversation.defaultEndpointAppearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single(binds = [EndpointAppearanceStore::class])
class DataStoreEndpointAppearanceStore(
    @Named("RadioDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : EndpointAppearanceStore {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val mutationMutex = Mutex()

    override val appearances: StateFlow<Map<RadioEndpointId, EndpointAppearance>> =
        dataStore.data
            .map { preferences -> decodeAppearances(preferences[KEY_APPEARANCES]) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override suspend fun reconcile(profiles: List<RadioEndpointProfile>) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = decodeAppearances(preferences[KEY_APPEARANCES])
                val profileIds = profiles.mapTo(mutableSetOf()) { it.id }
                val updated = current.filterKeys(profileIds::contains).toMutableMap()
                val usedTokens = updated.values.mapTo(mutableSetOf()) { it.accentToken }
                profiles.forEachIndexed { index, profile ->
                    if (profile.id !in updated) {
                        val token =
                            NodeAccentToken.entries.firstOrNull { it !in usedTokens }
                                ?: defaultEndpointAppearance(index).accentToken
                        updated[profile.id] = defaultEndpointAppearance(index).copy(accentToken = token)
                        usedTokens += token
                    }
                }
                writeAppearances(preferences, updated)
            }
        }
    }

    override suspend fun update(endpointId: RadioEndpointId, appearance: EndpointAppearance) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = decodeAppearances(preferences[KEY_APPEARANCES])
                writeAppearances(preferences, current + (endpointId to appearance.normalized()))
            }
        }
    }

    override suspend fun remove(endpointId: RadioEndpointId) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = decodeAppearances(preferences[KEY_APPEARANCES])
                writeAppearances(preferences, current - endpointId)
            }
        }
    }

    private companion object {
        val KEY_APPEARANCES = stringPreferencesKey("radio_endpoint_appearance_v1")
        const val FIELD_SEPARATOR = "|"
        const val RECORD_SEPARATOR = "\n"
        const val FIELD_COUNT = 5
        const val MAX_PURPOSE_LENGTH = 48

        fun writeAppearances(
            preferences: androidx.datastore.preferences.core.MutablePreferences,
            appearances: Map<RadioEndpointId, EndpointAppearance>,
        ) {
            if (appearances.isEmpty()) {
                preferences.remove(KEY_APPEARANCES)
            } else {
                preferences[KEY_APPEARANCES] = encodeAppearances(appearances)
            }
        }

        fun encodeAppearances(appearances: Map<RadioEndpointId, EndpointAppearance>): String = appearances.entries
            .sortedBy { it.value.sortOrder }
            .joinToString(RECORD_SEPARATOR) { (endpointId, appearance) ->
                listOf(
                    endpointId.value.toEncodedField(),
                    appearance.accentToken.name,
                    appearance.purposeLabel.toEncodedField(),
                    appearance.sortOrder.toString(),
                    appearance.showInAll.toString(),
                )
                    .joinToString(FIELD_SEPARATOR)
            }

        fun decodeAppearances(encoded: String?): Map<RadioEndpointId, EndpointAppearance> =
            encoded.orEmpty().lineSequence().mapNotNull(::decodeAppearance).toMap()

        fun decodeAppearance(record: String): Pair<RadioEndpointId, EndpointAppearance>? {
            val fields = record.split(FIELD_SEPARATOR)
            if (fields.size != FIELD_COUNT) return null
            return runCatching {
                RadioEndpointId(fields[0].fromEncodedField()) to
                    EndpointAppearance(
                        accentToken = NodeAccentToken.valueOf(fields[1]),
                        purposeLabel = fields[2].fromEncodedField().take(MAX_PURPOSE_LENGTH),
                        sortOrder = fields[3].toInt(),
                        showInAll = fields[4].toBooleanStrict(),
                    )
            }
                .getOrNull()
        }

        fun EndpointAppearance.normalized(): EndpointAppearance =
            copy(purposeLabel = purposeLabel.trim().take(MAX_PURPOSE_LENGTH))

        fun String.toEncodedField(): String = encodeUtf8().base64Url()

        fun String.fromEncodedField(): String = requireNotNull(decodeBase64()).utf8()
    }
}
