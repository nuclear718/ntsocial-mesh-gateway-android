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
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.prefs.InMemoryPreferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UiPrefsImplTest {
    @Test
    fun `launch preferences remain unknown until the first authoritative read`() = runTest {
        val releaseData = CompletableDeferred<Unit>()
        val persisted =
            mutablePreferencesOf(UiPrefsImpl.KEY_APP_INTRO_COMPLETED to true, UiPrefsImpl.KEY_LOCALE to "zh-TW")
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs =
            UiPrefsImpl(
                GatedPreferencesDataStore(releaseData, persisted),
                CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
            )

        assertNull(prefs.appLaunchPreferences.value)

        releaseData.complete(Unit)
        runCurrent()

        assertEquals(true, prefs.appLaunchPreferences.value?.appIntroCompleted)
        assertEquals("zh-TW", prefs.appLaunchPreferences.value?.locale)
    }

    @Test
    fun `awaited locale write is durable before returning`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = UiPrefsImpl(dataStore, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))

        prefs.setLocaleAndAwait("ja")

        val restarted = UiPrefsImpl(dataStore, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        assertEquals("ja", restarted.appLaunchPreferences.value?.locale)
    }

    @Test
    fun `cleanup pending is durable and clearing it cannot restore consent`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val firstInstance = UiPrefsImpl(dataStore, dispatchers)

        firstInstance.setPreciseLocationSharing(
            nodeNum = NODE_NUM,
            provide = false,
            channelIndex = CHANNEL_INDEX,
            channelIdentity = CHANNEL_IDENTITY,
            cleanupPending = true,
        )

        val restarted = UiPrefsImpl(dataStore, dispatchers)
        assertEquals(
            com.ntsocial.meshlink.core.repository.PreciseLocationAdmission(
                enabled = false,
                channelIndex = CHANNEL_INDEX,
                channelIdentity = CHANNEL_IDENTITY,
                cleanupPending = true,
            ),
            restarted.preciseLocationAdmission(NODE_NUM).value,
        )
        assertFalse(restarted.shouldProvideNodeLocation(NODE_NUM).value)

        restarted.clearPreciseLocationCleanupPending(NODE_NUM)

        assertFalse(restarted.preciseLocationAdmission(NODE_NUM).value.enabled)
        assertFalse(restarted.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertFalse(restarted.shouldProvideNodeLocation(NODE_NUM).value)
    }

    @Test
    fun `legacy enabled admission without cleanup key remains enabled`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("provide-location-$NODE_NUM")] = true
            preferences[intPreferencesKey("precise-location-channel-$NODE_NUM")] = CHANNEL_INDEX
            preferences[stringPreferencesKey("precise-location-channel-identity-$NODE_NUM")] = CHANNEL_IDENTITY
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = UiPrefsImpl(dataStore, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))

        val admission = prefs.preciseLocationAdmission(NODE_NUM).value

        assertTrue(admission.enabled)
        assertFalse(admission.cleanupPending)
    }

    @Test
    fun `legacy revoked admission without cleanup key migrates to cleanup pending`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("provide-location-$NODE_NUM")] = false
            preferences[intPreferencesKey("precise-location-channel-$NODE_NUM")] = CHANNEL_INDEX
            preferences[stringPreferencesKey("precise-location-channel-identity-$NODE_NUM")] = CHANNEL_IDENTITY
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = UiPrefsImpl(dataStore, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))

        val admission = prefs.preciseLocationAdmission(NODE_NUM).value

        assertFalse(admission.enabled)
        assertTrue(admission.cleanupPending)
        assertFalse(prefs.shouldProvideNodeLocation(NODE_NUM).value)
    }

    @Test
    fun `legacy provide flag without verified channel migrates to cleanup pending`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { preferences -> preferences[booleanPreferencesKey("provide-location-$NODE_NUM")] = true }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = UiPrefsImpl(dataStore, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))

        val admission = prefs.preciseLocationAdmission(NODE_NUM).value

        assertFalse(admission.enabled)
        assertTrue(admission.cleanupPending)
        assertFalse(prefs.shouldProvideNodeLocation(NODE_NUM).value)
    }

    @Test
    fun `authoritative admission read waits for persisted cleanup state`() = runTest {
        val releaseData = CompletableDeferred<Unit>()
        val persisted =
            mutablePreferencesOf(
                booleanPreferencesKey("provide-location-$NODE_NUM") to false,
                intPreferencesKey("precise-location-channel-$NODE_NUM") to CHANNEL_INDEX,
                stringPreferencesKey("precise-location-channel-identity-$NODE_NUM") to CHANNEL_IDENTITY,
                booleanPreferencesKey("precise-location-cleanup-pending-$NODE_NUM") to true,
            )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs =
            UiPrefsImpl(
                GatedPreferencesDataStore(releaseData, persisted),
                CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
            )
        val admission = async { prefs.readPreciseLocationAdmission(NODE_NUM) }
        runCurrent()

        assertFalse(admission.isCompleted)
        releaseData.complete(Unit)
        assertTrue(admission.await().cleanupPending)
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
        const val NODE_NUM = 0x5D6E
        const val CHANNEL_INDEX = 4
        const val CHANNEL_IDENTITY = "slot-4-identity"
    }
}
