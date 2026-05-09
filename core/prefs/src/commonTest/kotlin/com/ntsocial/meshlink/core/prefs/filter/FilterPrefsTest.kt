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
package com.ntsocial.meshlink.core.prefs.filter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.prefs.InMemoryPreferencesDataStore
import com.ntsocial.meshlink.core.repository.FilterPrefs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterPrefsTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var filterPrefs: FilterPrefs
    private lateinit var dispatchers: CoroutineDispatchers

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        dataStore = InMemoryPreferencesDataStore()
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        filterPrefs = FilterPrefsImpl(dataStore, dispatchers)
    }

    @Test fun `filterEnabled defaults to false`() = testScope.runTest { assertFalse(filterPrefs.filterEnabled.value) }

    @Test
    fun `filterWords defaults to empty set`() =
        testScope.runTest { assertTrue(filterPrefs.filterWords.value.isEmpty()) }

    @Test
    fun `setting filterEnabled updates preference`() = testScope.runTest {
        filterPrefs.setFilterEnabled(true)
        assertTrue(filterPrefs.filterEnabled.value)
    }

    @Test
    fun `setting filterWords updates preference`() = testScope.runTest {
        val words = setOf("test", "word")
        filterPrefs.setFilterWords(words)
        assertEquals(words, filterPrefs.filterWords.value)
    }
}
