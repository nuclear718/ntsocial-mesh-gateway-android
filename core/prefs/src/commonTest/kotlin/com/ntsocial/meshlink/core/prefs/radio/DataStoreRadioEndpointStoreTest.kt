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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.prefs.InMemoryPreferencesDataStore
import com.ntsocial.meshlink.core.radiofleet.DiscoveredRadio
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DataStoreRadioEndpointStoreTest {
    @Test
    fun `legacy selection migrates once and remains the sole primary`() = runTest {
        val store = createStore(StandardTestDispatcher(testScheduler))

        val first = store.migrateLegacySelection(ADDRESS_A, "Alpha")
        val second = store.migrateLegacySelection(ADDRESS_B, "Beta")

        assertEquals(first?.id, second?.id)
        assertEquals(listOf(ADDRESS_A), store.profiles.value.map { it.transportAddress })
        assertEquals(1, store.profiles.value.count { it.legacyPrimary })
        assertNull(store.selectedEndpointId.value)
        assertFalse(store.profiles.value.single().enabled)
    }

    @Test
    fun `remembered catalog de-duplicates addresses and retains more than four devices`() = runTest {
        val store = createStore(StandardTestDispatcher(testScheduler))
        val alpha = store.register(DiscoveredRadio(transportAddress = ADDRESS_A, displayName = "Alpha"))
        val refreshed =
            store.register(DiscoveredRadio(transportAddress = "xaa:bb:cc:dd:ee:01", displayName = "Alpha refreshed"))
        store.register(DiscoveredRadio(transportAddress = ADDRESS_B, displayName = "Beta"))
        store.register(DiscoveredRadio(transportAddress = ADDRESS_C, displayName = "Gamma"))
        store.register(DiscoveredRadio(transportAddress = ADDRESS_D, displayName = "Delta"))
        runCurrent()

        assertEquals(alpha.id, refreshed.id)
        assertEquals(4, store.profiles.value.size)
        assertEquals("Alpha refreshed", store.profiles.value.first { it.id == alpha.id }.displayName)
        store.register(DiscoveredRadio(transportAddress = ADDRESS_E, displayName = "Epsilon"))
        assertEquals(5, store.profiles.value.size)
        assertEquals(alpha.id, store.profiles.value.first().id)
    }

    @Test
    fun `disconnecting primary retains binding and remembered identity but selects another active endpoint`() =
        runTest {
            val store = createStore(StandardTestDispatcher(testScheduler))
            val alpha = store.register(DiscoveredRadio(transportAddress = ADDRESS_A, displayName = "Alpha"))
            val beta = store.register(DiscoveredRadio(transportAddress = ADDRESS_B, displayName = "Beta"))
            store.update(alpha.copy(enabled = true, autoConnect = true))
            store.update(beta.copy(enabled = true, autoConnect = true))
            store.select(alpha.id)
            store.update(alpha.copy(enabled = false, autoConnect = false))
            runCurrent()
            assertEquals(beta.id, store.selectedEndpointId.value)
            assertEquals(listOf(alpha.id), store.profiles.value.filter { it.legacyPrimary }.map { it.id })
            assertFailsWith<IllegalArgumentException> { store.remove(alpha.id) }
            val same = store.register(DiscoveredRadio(transportAddress = ADDRESS_A, displayName = "Alpha"))
            assertEquals(alpha.id, same.id)
            assertFalse(same.enabled)
        }

    @Test
    fun `v1 catalog migration preserves identities and snapshot without guessing disconnected intent`() = runTest {
        val preferences = InMemoryPreferencesDataStore()
        val record =
            listOf(
                "stable-id",
                "MESHTASTIC",
                ADDRESS_A.encodeUtf8().base64Url(),
                "Alpha".encodeUtf8().base64Url(),
                "true",
                "true",
                "100",
                "true",
            )
                .joinToString("|")
        preferences.edit {
            it[stringPreferencesKey("radio_endpoint_profiles_v1")] = record
            it[intPreferencesKey("radio_endpoint_migration_version")] = 1
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DataStoreRadioEndpointStore(preferences, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        store.migrateLegacySelection(ADDRESS_B, "Wrong legacy address")
        assertEquals("stable-id", store.profiles.value.single().id.value)
        assertFalse(store.profiles.value.single().autoConnect)
        assertFalse(store.profiles.value.single().enabled)
        val restarted =
            DataStoreRadioEndpointStore(preferences, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        restarted.migrateLegacySelection(ADDRESS_B, "Ignored")
        assertEquals(store.profiles.value, restarted.profiles.value)
    }

    private fun createStore(dispatcher: TestDispatcher): DataStoreRadioEndpointStore = DataStoreRadioEndpointStore(
        dataStore = InMemoryPreferencesDataStore(),
        dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
    )

    private companion object {
        const val ADDRESS_A = "xAA:BB:CC:DD:EE:01"
        const val ADDRESS_B = "xAA:BB:CC:DD:EE:02"
        const val ADDRESS_C = "xAA:BB:CC:DD:EE:03"
        const val ADDRESS_D = "xAA:BB:CC:DD:EE:04"
        const val ADDRESS_E = "xAA:BB:CC:DD:EE:05"
    }
}
