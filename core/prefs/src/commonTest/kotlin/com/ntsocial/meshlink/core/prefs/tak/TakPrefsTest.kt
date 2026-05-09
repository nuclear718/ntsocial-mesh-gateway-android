/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.core.prefs.tak

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.prefs.InMemoryPreferencesDataStore
import com.ntsocial.meshlink.core.repository.TakPrefs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TakPrefsTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var takPrefs: TakPrefs
    private lateinit var dispatchers: CoroutineDispatchers

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        dataStore = InMemoryPreferencesDataStore()
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        takPrefs = TakPrefsImpl(dataStore, dispatchers)
    }

    @Test
    fun `isTakServerEnabled defaults to false`() = testScope.runTest { assertFalse(takPrefs.isTakServerEnabled.value) }

    @Test
    fun `setting isTakServerEnabled updates preference`() = testScope.runTest {
        takPrefs.setTakServerEnabled(true)
        assertTrue(takPrefs.isTakServerEnabled.value)

        takPrefs.setTakServerEnabled(false)
        assertFalse(takPrefs.isTakServerEnabled.value)
    }
}
