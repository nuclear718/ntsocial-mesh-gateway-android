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
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import com.ntsocial.meshlink.core.radiofleet.conversation.NodeAccentToken
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreEndpointAppearanceStoreTest {
    @Test
    fun `four profiles receive stable distinct default accents`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val profiles = (0 until 4).map(::profile)
        val store = createStore(dataStore, dispatcher)

        store.reconcile(profiles)
        runCurrent()

        assertEquals(4, store.appearances.value.size)
        assertEquals(4, store.appearances.value.values.map { it.accentToken }.toSet().size)
        assertEquals(
            listOf(NodeAccentToken.INDIGO, NodeAccentToken.EMERALD, NodeAccentToken.AMBER, NodeAccentToken.CYAN),
            profiles.map { store.appearances.value.getValue(it.id).accentToken },
        )
    }

    @Test
    fun `custom appearance survives store recreation without changing endpoint catalog`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val radioStore = DataStoreRadioEndpointStore(dataStore, dispatchers(dispatcher))
        val profile = radioStore.register(DiscoveredRadio(transportAddress = ADDRESS, displayName = "Alpha"))
        val appearanceStore = createStore(dataStore, dispatcher)
        appearanceStore.reconcile(listOf(profile))
        runCurrent()
        val catalogBefore = radioStore.profiles.value

        val customized =
            appearanceStore.appearances.value
                .getValue(profile.id)
                .copy(accentToken = NodeAccentToken.ROSE, purposeLabel = "Mountain relay")
        appearanceStore.update(profile.id, customized)
        runCurrent()
        val recreated = createStore(dataStore, dispatcher)
        runCurrent()

        assertEquals(customized, recreated.appearances.value.getValue(profile.id))
        assertEquals(catalogBefore, radioStore.profiles.value)
    }

    private fun createStore(dataStore: InMemoryPreferencesDataStore, dispatcher: TestDispatcher) =
        DataStoreEndpointAppearanceStore(dataStore = dataStore, dispatchers = dispatchers(dispatcher))

    private fun dispatchers(dispatcher: TestDispatcher) = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)

    private fun profile(index: Int) = RadioEndpointProfile(
        id = RadioEndpointId("endpoint-$index"),
        protocol = RadioProtocol.MESHTASTIC,
        transportAddress = "xAA:BB:CC:DD:EE:0$index",
        displayName = "Node $index",
        legacyPrimary = index == 0,
    )

    private companion object {
        const val ADDRESS = "xAA:BB:CC:DD:EE:01"
    }
}
