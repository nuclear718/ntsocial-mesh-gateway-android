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

import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.prefs.InMemoryPreferencesDataStore
import com.ntsocial.meshlink.core.radiofleet.DiscoveredRadio
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointLimitExceededException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataStoreRadioEndpointStoreTest {
    @Test
    fun `legacy selection migrates once and remains the sole primary`() = runTest {
        val store = createStore(StandardTestDispatcher(testScheduler))

        val first = store.migrateLegacySelection(ADDRESS_A, "Alpha")
        val second = store.migrateLegacySelection(ADDRESS_B, "Beta")

        assertEquals(first?.id, second?.id)
        assertEquals(listOf(ADDRESS_A), store.profiles.value.map { it.transportAddress })
        assertEquals(1, store.profiles.value.count { it.legacyPrimary })
        assertEquals(first?.id, store.selectedEndpointId.value)
    }

    @Test
    fun `register de-duplicates normalized address and caps the fleet at four`() = runTest {
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
        assertFailsWith<RadioEndpointLimitExceededException> {
            store.register(DiscoveredRadio(transportAddress = ADDRESS_E, displayName = "Epsilon"))
        }
    }

    @Test
    fun `removing primary promotes one replacement and repairs selection`() = runTest {
        val store = createStore(StandardTestDispatcher(testScheduler))
        val alpha = store.register(DiscoveredRadio(transportAddress = ADDRESS_A, displayName = "Alpha"))
        val beta = store.register(DiscoveredRadio(transportAddress = ADDRESS_B, displayName = "Beta"))
        store.select(alpha.id)

        store.remove(alpha.id)
        runCurrent()

        assertEquals(beta.id, store.selectedEndpointId.value)
        assertEquals(listOf(beta.id), store.profiles.value.filter { it.legacyPrimary }.map { it.id })
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
