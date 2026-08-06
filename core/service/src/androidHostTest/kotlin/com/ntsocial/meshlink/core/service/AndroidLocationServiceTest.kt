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
package com.ntsocial.meshlink.core.service

import android.Manifest
import android.app.Application
import com.ntsocial.meshlink.core.repository.Location
import com.ntsocial.meshlink.core.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import android.location.Location as AndroidLocation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocationServiceTest {
    @Test
    fun testInitialization() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val service = AndroidLocationService(context, FakeLocationRepository())
        assertNotNull(service)
    }

    @Test
    fun `approximate location permission is sufficient for one-shot location`() = runTest {
        val context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context)
            .denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val expected = AndroidLocation("test").apply { latitude = 25.0 }
        val service = AndroidLocationService(context, FakeLocationRepository(flowOf(expected)))

        assertEquals(expected, service.getCurrentLocation())
    }

    @Test
    fun `one-shot location remains unavailable when both permissions are denied`() = runTest {
        val context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context)
            .denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val service = AndroidLocationService(context, FakeLocationRepository())

        assertNull(service.getCurrentLocation())
    }

    private class FakeLocationRepository(private val locations: Flow<Location> = emptyFlow()) : LocationRepository {
        override val receivingLocationUpdates = MutableStateFlow(false)

        override fun getLocations(): Flow<Location> = locations
    }
}
