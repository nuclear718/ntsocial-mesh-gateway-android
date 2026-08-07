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
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RadioPrefsImplTest {

    @Test
    fun `authoritative read waits for DataStore instead of returning StateFlow initial null`() = runTest {
        val releaseData = CompletableDeferred<Unit>()
        val persisted = mutablePreferencesOf(RadioPrefsImpl.KEY_DEV_ADDR_PREF to SELECTED_ADDRESS)
        val dataStore = GatedPreferencesDataStore(releaseData, persisted)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefs = RadioPrefsImpl(dataStore, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val read = async { prefs.readPersistedDevAddr() }
        runCurrent()

        assertEquals(null, prefs.devAddr.value)
        assertFalse(read.isCompleted)

        releaseData.complete(Unit)
        assertEquals(SELECTED_ADDRESS, read.await())
    }

    private class GatedPreferencesDataStore(
        private val releaseData: CompletableDeferred<Unit>,
        private val persisted: Preferences,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            releaseData.await()
            emit(persisted)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(persisted)
    }

    private companion object {
        const val SELECTED_ADDRESS = "xAA:BB:CC:DD:EE:FF"
    }
}
