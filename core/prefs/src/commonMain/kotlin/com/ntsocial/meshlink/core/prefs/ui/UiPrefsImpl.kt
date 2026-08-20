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
package com.ntsocial.meshlink.core.prefs.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.prefs.cachedFlow
import com.ntsocial.meshlink.core.repository.PreciseLocationAdmission
import com.ntsocial.meshlink.core.repository.UiPrefs
import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
@Suppress("TooManyFunctions")
class UiPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : UiPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    // Maps nodeNum to a flow for the for the "provide-location-nodeNum" pref
    private val provideNodeLocationFlows = atomic(persistentMapOf<Int, Lazy<StateFlow<Boolean>>>())

    // Maps nodeNum to the exact-position radio channel selected for that node.
    private val preciseLocationChannelFlows = atomic(persistentMapOf<Int, Lazy<StateFlow<Int>>>())

    // Maps nodeNum to the atomic consent + selected-channel admission snapshot.
    private val preciseLocationAdmissionFlows =
        atomic(persistentMapOf<Int, Lazy<StateFlow<PreciseLocationAdmission>>>())

    override val appIntroCompleted: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_APP_INTRO_COMPLETED] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setAppIntroCompleted(completed: Boolean) {
        scope.launch { dataStore.edit { it[KEY_APP_INTRO_COMPLETED] = completed } }
    }

    override val theme: StateFlow<Int> =
        dataStore.data.map { it[KEY_THEME] ?: -1 }.stateIn(scope, SharingStarted.Lazily, -1)

    override fun setTheme(value: Int) {
        scope.launch { dataStore.edit { it[KEY_THEME] = value } }
    }

    override val locale: StateFlow<String> =
        dataStore.data.map { it[KEY_LOCALE] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    override fun setLocale(languageTag: String) {
        scope.launch { dataStore.edit { it[KEY_LOCALE] = languageTag } }
    }

    override val nodeSort: StateFlow<Int> =
        dataStore.data.map { it[KEY_NODE_SORT] ?: -1 }.stateIn(scope, SharingStarted.Lazily, -1)

    override fun setNodeSort(value: Int) {
        scope.launch { dataStore.edit { it[KEY_NODE_SORT] = value } }
    }

    override val includeUnknown: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_INCLUDE_UNKNOWN] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setIncludeUnknown(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_INCLUDE_UNKNOWN] = value } }
    }

    override val excludeInfrastructure: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_INFRASTRUCTURE] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setExcludeInfrastructure(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_INFRASTRUCTURE] = value } }
    }

    override val onlyOnline: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_ONLINE] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setOnlyOnline(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_ONLINE] = value } }
    }

    override val onlyDirect: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_DIRECT] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setOnlyDirect(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_DIRECT] = value } }
    }

    override val showIgnored: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_IGNORED] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setShowIgnored(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_IGNORED] = value } }
    }

    override val excludeMqtt: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_MQTT] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setExcludeMqtt(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_MQTT] = value } }
    }

    override val hasShownNotPairedWarning: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun setHasShownNotPairedWarning(shown: Boolean) {
        scope.launch { dataStore.edit { it[KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF] = shown } }
    }

    override val showQuickChat: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_QUICK_CHAT_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setShowQuickChat(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_QUICK_CHAT_PREF] = show } }
    }

    override val bleAutoScan: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_BLE_AUTO_SCAN] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setBleAutoScan(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_BLE_AUTO_SCAN] = enabled } }
    }

    override val networkAutoScan: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_NETWORK_AUTO_SCAN] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setNetworkAutoScan(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_NETWORK_AUTO_SCAN] = enabled } }
    }

    override val showBleTransport: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_BLE_TRANSPORT] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShowBleTransport(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_BLE_TRANSPORT] = enabled } }
    }

    override val showNetworkTransport: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_NETWORK_TRANSPORT] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShowNetworkTransport(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_NETWORK_TRANSPORT] = enabled } }
    }

    override val showUsbTransport: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_USB_TRANSPORT] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShowUsbTransport(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_USB_TRANSPORT] = enabled } }
    }

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        cachedFlow(provideNodeLocationFlows, nodeNum) {
            dataStore.data
                .map { preferences -> preferences.readPreciseLocationAdmission(nodeNum).enabled }
                .stateIn(scope, SharingStarted.Eagerly, false)
        }

    @Suppress("UNUSED_PARAMETER")
    override fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean) {
        // Legacy callers may revoke consent, but they cannot admit a GPS feed without the verified channel identity.
        scope.launch {
            dataStore.edit { preferences ->
                val current = preferences.readPreciseLocationAdmission(nodeNum)
                preferences[booleanPreferencesKey(provideLocationKey(nodeNum))] = false
                if (current.enabled || current.cleanupPending) {
                    preferences[booleanPreferencesKey(preciseLocationCleanupPendingKey(nodeNum))] = true
                }
            }
        }
    }

    override fun preciseLocationChannelIndex(nodeNum: Int): StateFlow<Int> =
        cachedFlow(preciseLocationChannelFlows, nodeNum) {
            val key = intPreferencesKey(preciseLocationChannelKey(nodeNum))
            dataStore.data
                .map { it[key] ?: NO_PRECISE_LOCATION_CHANNEL }
                .stateIn(scope, SharingStarted.Eagerly, NO_PRECISE_LOCATION_CHANNEL)
        }

    override fun setPreciseLocationChannelIndex(nodeNum: Int, channelIndex: Int) {
        scope.launch {
            dataStore.edit { preferences ->
                val current = preferences.readPreciseLocationAdmission(nodeNum)
                preferences[intPreferencesKey(preciseLocationChannelKey(nodeNum))] = channelIndex
                preferences[booleanPreferencesKey(provideLocationKey(nodeNum))] = false
                preferences[stringPreferencesKey(preciseLocationChannelIdentityKey(nodeNum))] = ""
                preferences[booleanPreferencesKey(preciseLocationCleanupPendingKey(nodeNum))] =
                    current.enabled || current.cleanupPending
            }
        }
    }

    override fun preciseLocationAdmission(nodeNum: Int): StateFlow<PreciseLocationAdmission> =
        cachedFlow(preciseLocationAdmissionFlows, nodeNum) {
            dataStore.data
                .map { preferences -> preferences.readPreciseLocationAdmission(nodeNum) }
                .stateIn(scope, SharingStarted.Eagerly, PreciseLocationAdmission())
        }

    override suspend fun readPreciseLocationAdmission(nodeNum: Int): PreciseLocationAdmission =
        dataStore.data.first().readPreciseLocationAdmission(nodeNum)

    override suspend fun setPreciseLocationSharing(
        nodeNum: Int,
        provide: Boolean,
        channelIndex: Int,
        channelIdentity: String,
        cleanupPending: Boolean,
    ) {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(provideLocationKey(nodeNum))] = provide && !cleanupPending
            preferences[intPreferencesKey(preciseLocationChannelKey(nodeNum))] = channelIndex
            preferences[stringPreferencesKey(preciseLocationChannelIdentityKey(nodeNum))] = channelIdentity
            preferences[booleanPreferencesKey(preciseLocationCleanupPendingKey(nodeNum))] = cleanupPending
        }
    }

    override suspend fun clearPreciseLocationCleanupPending(nodeNum: Int) {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(provideLocationKey(nodeNum))] = false
            preferences[booleanPreferencesKey(preciseLocationCleanupPendingKey(nodeNum))] = false
        }
    }

    private fun Preferences.readPreciseLocationAdmission(nodeNum: Int): PreciseLocationAdmission {
        val provide = this[booleanPreferencesKey(provideLocationKey(nodeNum))] == true
        val channelIndex = this[intPreferencesKey(preciseLocationChannelKey(nodeNum))] ?: NO_PRECISE_LOCATION_CHANNEL
        val channelIdentity = this[stringPreferencesKey(preciseLocationChannelIdentityKey(nodeNum))].orEmpty()
        val hasVerifiedChannelSelection = channelIndex > 0 && channelIdentity.isNotBlank()
        val cleanupPending =
            this[booleanPreferencesKey(preciseLocationCleanupPendingKey(nodeNum))]
                ?: (provide != hasVerifiedChannelSelection)
        return PreciseLocationAdmission(
            enabled = provide && hasVerifiedChannelSelection && !cleanupPending,
            channelIndex = channelIndex,
            channelIdentity = channelIdentity,
            cleanupPending = cleanupPending,
        )
    }

    private fun provideLocationKey(nodeNum: Int) = "provide-location-$nodeNum"

    private fun preciseLocationChannelKey(nodeNum: Int) = "precise-location-channel-$nodeNum"

    private fun preciseLocationChannelIdentityKey(nodeNum: Int) = "precise-location-channel-identity-$nodeNum"

    private fun preciseLocationCleanupPendingKey(nodeNum: Int) = "precise-location-cleanup-pending-$nodeNum"

    companion object {
        const val NO_PRECISE_LOCATION_CHANNEL = -1

        val KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF = booleanPreferencesKey("has_shown_not_paired_warning")
        val KEY_SHOW_QUICK_CHAT_PREF = booleanPreferencesKey("show-quick-chat")

        val KEY_APP_INTRO_COMPLETED = booleanPreferencesKey("app_intro_completed")
        val KEY_THEME = intPreferencesKey("theme")
        val KEY_LOCALE = stringPreferencesKey("locale")
        val KEY_NODE_SORT = intPreferencesKey("node-sort-option")
        val KEY_INCLUDE_UNKNOWN = booleanPreferencesKey("include-unknown")
        val KEY_EXCLUDE_INFRASTRUCTURE = booleanPreferencesKey("exclude-infrastructure")
        val KEY_ONLY_ONLINE = booleanPreferencesKey("only-online")
        val KEY_ONLY_DIRECT = booleanPreferencesKey("only-direct")
        val KEY_SHOW_IGNORED = booleanPreferencesKey("show-ignored")
        val KEY_EXCLUDE_MQTT = booleanPreferencesKey("exclude-mqtt")
        val KEY_BLE_AUTO_SCAN = booleanPreferencesKey("ble-auto-scan")
        val KEY_NETWORK_AUTO_SCAN = booleanPreferencesKey("network-auto-scan")
        val KEY_SHOW_BLE_TRANSPORT = booleanPreferencesKey("show-ble-transport")
        val KEY_SHOW_NETWORK_TRANSPORT = booleanPreferencesKey("show-network-transport")
        val KEY_SHOW_USB_TRANSPORT = booleanPreferencesKey("show-usb-transport")
    }
}
