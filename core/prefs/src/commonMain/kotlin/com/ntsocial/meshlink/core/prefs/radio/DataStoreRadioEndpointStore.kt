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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.common.util.normalizeAddress
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.radiofleet.DEFAULT_RADIO_PRIORITY
import com.ntsocial.meshlink.core.radiofleet.DiscoveredRadio
import com.ntsocial.meshlink.core.radiofleet.MAX_RADIO_ENDPOINTS
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointLimitExceededException
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointStore
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [RadioEndpointStore::class])
class DataStoreRadioEndpointStore(
    @Named("RadioDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : RadioEndpointStore {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val mutationMutex = Mutex()

    override val profiles: StateFlow<List<RadioEndpointProfile>> =
        dataStore.data
            .map { preferences -> normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES])) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val selectedEndpointId: StateFlow<RadioEndpointId?> =
        dataStore.data
            .map { preferences ->
                val currentProfiles = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
                resolveSelectedEndpoint(preferences[KEY_SELECTED_ENDPOINT], currentProfiles)
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun migrateLegacySelection(address: String?, name: String?): RadioEndpointProfile? =
        mutationMutex.withLock {
            var migrated: RadioEndpointProfile? = null
            dataStore.edit { preferences ->
                val current = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
                val migrationVersion = preferences[KEY_MIGRATION_VERSION] ?: 0
                if (migrationVersion >= ENDPOINT_MIGRATION_VERSION) {
                    migrated = current.firstOrNull { it.legacyPrimary }
                    return@edit
                }

                val validLegacyAddress = address?.takeIf(::isUsableAddress)
                val updated =
                    if (current.isEmpty() && validLegacyAddress != null) {
                        listOf(
                            RadioEndpointProfile(
                                id = RadioEndpointId(Uuid.random().toString()),
                                protocol = RadioProtocol.MESHTASTIC,
                                transportAddress = validLegacyAddress,
                                displayName = name.orEmpty().ifBlank { validLegacyAddress },
                                legacyPrimary = true,
                            ),
                        )
                    } else {
                        current
                    }
                migrated = updated.firstOrNull { it.legacyPrimary }
                writeProfiles(preferences, updated, preferences[KEY_SELECTED_ENDPOINT])
                preferences[KEY_MIGRATION_VERSION] = ENDPOINT_MIGRATION_VERSION
            }
            migrated?.let { expected -> profiles.first { values -> values.any { it.id == expected.id } } }
            migrated
        }

    override suspend fun register(candidate: DiscoveredRadio): RadioEndpointProfile = mutationMutex.withLock {
        require(isUsableAddress(candidate.transportAddress)) { "Radio address is not selectable" }
        var result: RadioEndpointProfile? = null
        dataStore.edit { preferences ->
            val current = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
            val normalizedCandidate = normalizeAddress(candidate.transportAddress)
            val existing =
                current.firstOrNull {
                    it.protocol == candidate.protocol && it.normalizedAddress == normalizedCandidate
                }
            val updated =
                if (existing != null) {
                    val refreshed =
                        existing.copy(
                            transportAddress = candidate.transportAddress,
                            displayName = candidate.displayName.ifBlank { existing.displayName },
                            enabled = true,
                        )
                    result = refreshed
                    current.map { if (it.id == existing.id) refreshed else it }
                } else {
                    if (current.size >= MAX_RADIO_ENDPOINTS) throw RadioEndpointLimitExceededException()
                    val created =
                        RadioEndpointProfile(
                            id = RadioEndpointId(Uuid.random().toString()),
                            protocol = candidate.protocol,
                            transportAddress = candidate.transportAddress,
                            displayName = candidate.displayName.ifBlank { candidate.transportAddress },
                            legacyPrimary = current.isEmpty(),
                        )
                    result = created
                    current + created
                }
            writeProfiles(preferences, updated, result.id.value)
        }
        checkNotNull(result)
    }

    override suspend fun update(profile: RadioEndpointProfile) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
                require(current.any { it.id == profile.id }) { "Unknown endpoint ${profile.id}" }
                val updated = current.map { if (it.id == profile.id) profile else it }
                writeProfiles(preferences, updated, preferences[KEY_SELECTED_ENDPOINT])
            }
        }
    }

    override suspend fun select(endpointId: RadioEndpointId) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
                require(current.any { it.id == endpointId }) { "Unknown endpoint $endpointId" }
                preferences[KEY_SELECTED_ENDPOINT] = endpointId.value
            }
        }
    }

    override suspend fun setLegacyPrimary(endpointId: RadioEndpointId) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
                require(current.any { it.id == endpointId }) { "Unknown endpoint $endpointId" }
                val updated = current.map { it.copy(legacyPrimary = it.id == endpointId) }
                writeProfiles(preferences, updated, preferences[KEY_SELECTED_ENDPOINT])
            }
        }
    }

    override suspend fun remove(endpointId: RadioEndpointId) {
        mutationMutex.withLock {
            dataStore.edit { preferences ->
                val current = normalizeProfiles(decodeProfiles(preferences[KEY_ENDPOINT_PROFILES]))
                val removed = current.firstOrNull { it.id == endpointId } ?: return@edit
                var updated = current.filterNot { it.id == endpointId }
                if (removed.legacyPrimary && updated.isNotEmpty()) {
                    val replacement = updated.maxBy { it.priority }
                    updated = updated.map { it.copy(legacyPrimary = it.id == replacement.id) }
                }
                writeProfiles(preferences, updated, preferences[KEY_SELECTED_ENDPOINT])
            }
        }
    }

    private fun writeProfiles(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        profiles: List<RadioEndpointProfile>,
        selectedId: String?,
    ) {
        val normalized = normalizeProfiles(profiles)
        preferences[KEY_ENDPOINT_PROFILES] = encodeProfiles(normalized)
        val resolvedSelection = resolveSelectedEndpoint(selectedId, normalized)
        if (resolvedSelection == null) {
            preferences.remove(KEY_SELECTED_ENDPOINT)
        } else {
            preferences[KEY_SELECTED_ENDPOINT] = resolvedSelection.value
        }
    }

    private companion object {
        const val ENDPOINT_MIGRATION_VERSION = 1
        const val FIELD_SEPARATOR = "|"
        const val RECORD_SEPARATOR = "\n"

        val KEY_ENDPOINT_PROFILES = stringPreferencesKey("radio_endpoint_profiles_v1")
        val KEY_SELECTED_ENDPOINT = stringPreferencesKey("radio_selected_endpoint_v1")
        val KEY_MIGRATION_VERSION = intPreferencesKey("radio_endpoint_migration_version")

        fun isUsableAddress(address: String): Boolean = normalizeAddress(address) != "DEFAULT"

        fun resolveSelectedEndpoint(selectedId: String?, profiles: List<RadioEndpointProfile>): RadioEndpointId? =
            profiles.firstOrNull { it.id.value == selectedId }?.id
                ?: profiles.firstOrNull { it.legacyPrimary }?.id
                ?: profiles.firstOrNull()?.id

        fun normalizeProfiles(input: List<RadioEndpointProfile>): List<RadioEndpointProfile> {
            val unique =
                input
                    .filter { isUsableAddress(it.transportAddress) }
                    .distinctBy { it.protocol to it.normalizedAddress }
                    .take(MAX_RADIO_ENDPOINTS)
            if (unique.isEmpty()) return emptyList()
            val primaryId = unique.firstOrNull { it.legacyPrimary }?.id ?: unique.first().id
            return unique.map { it.copy(legacyPrimary = it.id == primaryId) }
        }

        fun encodeProfiles(profiles: List<RadioEndpointProfile>): String =
            profiles.joinToString(RECORD_SEPARATOR) { profile ->
                listOf(
                    profile.id.value,
                    profile.protocol.name,
                    profile.transportAddress.toEncodedField(),
                    profile.displayName.toEncodedField(),
                    profile.enabled.toString(),
                    profile.autoConnect.toString(),
                    profile.priority.toString(),
                    profile.legacyPrimary.toString(),
                )
                    .joinToString(FIELD_SEPARATOR)
            }

        fun decodeProfiles(encoded: String?): List<RadioEndpointProfile> =
            encoded.orEmpty().lineSequence().mapNotNull(::decodeProfile).toList()

        fun decodeProfile(record: String): RadioEndpointProfile? {
            val fields = record.split(FIELD_SEPARATOR)
            if (fields.size != PROFILE_FIELD_COUNT) return null
            return runCatching {
                RadioEndpointProfile(
                    id = RadioEndpointId(fields[0]),
                    protocol = RadioProtocol.valueOf(fields[1]),
                    transportAddress = fields[2].fromEncodedField(),
                    displayName = fields[3].fromEncodedField(),
                    enabled = fields[4].toBooleanStrict(),
                    autoConnect = fields[5].toBooleanStrict(),
                    priority = fields[6].toIntOrNull() ?: DEFAULT_RADIO_PRIORITY,
                    legacyPrimary = fields[7].toBooleanStrict(),
                )
            }
                .getOrNull()
        }

        fun String.toEncodedField(): String = encodeUtf8().base64Url()

        fun String.fromEncodedField(): String = requireNotNull(decodeBase64()).utf8()

        const val PROFILE_FIELD_COUNT = 8
    }
}
