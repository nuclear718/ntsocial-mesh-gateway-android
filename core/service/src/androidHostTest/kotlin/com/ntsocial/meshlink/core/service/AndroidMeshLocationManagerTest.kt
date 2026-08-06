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
import android.location.Location
import com.ntsocial.meshlink.core.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.proto.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMeshLocationManagerTest {
    @Test
    fun `coarse permission granted after initial start can restart one listener and emit external position`() =
        runTest {
            val application = RuntimeEnvironment.getApplication() as Application
            val shadowApplication = shadowOf(application)
            shadowApplication.denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            val repository = FakeLocationRepository()
            val manager = AndroidMeshLocationManager(application, repository)
            val positions = mutableListOf<Position>()

            manager.setLocationAccessAllowed(true)
            manager.start(this) { positions += it }
            advanceUntilIdle()
            assertEquals(0, repository.subscriptionCount)

            shadowApplication.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            manager.restart()
            advanceUntilIdle()
            assertEquals(1, repository.subscriptionCount)

            manager.restart()
            advanceUntilIdle()
            assertEquals(1, repository.subscriptionCount, "restart must not install a duplicate active listener")

            repository.locations.emit(
                Location("test").apply {
                    latitude = 25.033
                    longitude = 121.5654
                    altitude = 12.0
                    time = 123_000L
                    speed = 3f
                    bearing = 90f
                },
            )
            advanceUntilIdle()

            val position = positions.single()
            assertEquals(com.ntsocial.meshlink.core.model.Position.degI(25.033), position.latitude_i)
            assertEquals(com.ntsocial.meshlink.core.model.Position.degI(121.5654), position.longitude_i)
            assertEquals(123, position.time)
            assertEquals(Position.LocSource.LOC_EXTERNAL, position.location_source)

            manager.stop()
            advanceUntilIdle()
            assertEquals(0, repository.subscriptionCount)
        }

    @Test
    fun `foreground access suspension stops updates but retains desired callback for resume`() = runTest {
        val application = RuntimeEnvironment.getApplication() as Application
        val shadowApplication = shadowOf(application)
        shadowApplication.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val repository = FakeLocationRepository()
        val manager = AndroidMeshLocationManager(application, repository)

        manager.setLocationAccessAllowed(true)
        manager.start(this) {}
        advanceUntilIdle()
        assertEquals(1, repository.subscriptionCount)

        manager.setLocationAccessAllowed(false)
        advanceUntilIdle()
        assertEquals(0, repository.subscriptionCount)

        manager.setLocationAccessAllowed(true)
        advanceUntilIdle()
        assertEquals(1, repository.subscriptionCount)

        manager.stop()
    }

    @Test
    fun `access demotion racing a start cannot leave a location listener active`() = runTest {
        val application = RuntimeEnvironment.getApplication() as Application
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val repository = BlockingLocationRepository()
        val manager = AndroidMeshLocationManager(application, repository)
        val testScope = this
        var startError: Throwable? = null

        manager.setLocationAccessAllowed(true)
        val startThread =
            thread(name = "location-start") {
                try {
                    manager.start(testScope) {}
                } catch (error: Throwable) {
                    startError = error
                }
            }
        assertTrue(repository.getLocationsStarted.await(5, TimeUnit.SECONDS))

        val demotionReturned = CountDownLatch(1)
        val demotionThread =
            thread(name = "location-demotion") {
                try {
                    manager.setLocationAccessAllowed(false)
                } finally {
                    demotionReturned.countDown()
                }
            }
        try {
            assertFalse(
                demotionReturned.await(100, TimeUnit.MILLISECONDS),
                "demotion must wait until the in-flight listener installation is owned and cancellable",
            )
        } finally {
            repository.allowGetLocationsReturn.countDown()
        }

        startThread.join(5_000)
        demotionThread.join(5_000)
        assertFalse(startThread.isAlive)
        assertFalse(demotionThread.isAlive)
        startError?.let { throw AssertionError("location start failed", it) }
        advanceUntilIdle()
        assertEquals(0, repository.subscriptionCount)
    }

    private class FakeLocationRepository : LocationRepository {
        override val receivingLocationUpdates = MutableStateFlow(false)
        val locations = MutableSharedFlow<Location>()
        var subscriptionCount = 0

        override fun getLocations(): Flow<Location> = flow {
            subscriptionCount += 1
            try {
                emitAll(locations)
            } finally {
                subscriptionCount -= 1
            }
        }
    }

    private class BlockingLocationRepository : LocationRepository {
        override val receivingLocationUpdates = MutableStateFlow(false)
        val getLocationsStarted = CountDownLatch(1)
        val allowGetLocationsReturn = CountDownLatch(1)
        private val locations = MutableSharedFlow<Location>()
        var subscriptionCount = 0

        override fun getLocations(): Flow<Location> {
            getLocationsStarted.countDown()
            allowGetLocationsReturn.await(5, TimeUnit.SECONDS)
            return flow {
                subscriptionCount += 1
                try {
                    emitAll(locations)
                } finally {
                    subscriptionCount -= 1
                }
            }
        }
    }
}
