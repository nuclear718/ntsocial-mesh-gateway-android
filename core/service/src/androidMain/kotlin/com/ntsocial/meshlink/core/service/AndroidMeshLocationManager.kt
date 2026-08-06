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

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.location.LocationCompat
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.hasLocationPermission
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.repository.LocationRepository
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

@Single
class AndroidMeshLocationManager(private val context: Application, private val locationRepository: LocationRepository) :
    MeshLocationManager {
    private val stateLock = Any()
    private var scope: CoroutineScope? = null
    private var sendPositionFn: ((ProtoPosition) -> Unit)? = null
    private var locationFlow: Job? = null
    private var locationAccessAllowed = false

    @SuppressLint("MissingPermission")
    override fun start(scope: CoroutineScope, sendPositionFn: (ProtoPosition) -> Unit) {
        synchronized(stateLock) {
            this.scope = scope
            this.sendPositionFn = sendPositionFn
            startLocked()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocked() {
        if (locationFlow?.isActive == true) return
        val currentScope = scope
        val currentSendPositionFn = sendPositionFn

        if (
            currentScope != null &&
            currentSendPositionFn != null &&
            locationAccessAllowed &&
            context.hasLocationPermission()
        ) {
            locationFlow =
                locationRepository
                    .getLocations()
                    .onEach { location ->
                        currentSendPositionFn(
                            ProtoPosition(
                                latitude_i = Position.degI(location.latitude),
                                longitude_i = Position.degI(location.longitude),
                                altitude =
                                if (LocationCompat.hasMslAltitude(location)) {
                                    LocationCompat.getMslAltitudeMeters(location).toInt()
                                } else {
                                    null
                                },
                                altitude_hae = location.altitude.toInt(),
                                time = (location.time.milliseconds.inWholeSeconds).toInt(),
                                ground_speed = location.speed.toInt(),
                                ground_track = location.bearing.toInt(),
                                location_source = ProtoPosition.LocSource.LOC_EXTERNAL,
                            ),
                        )
                    }
                    .catch { error -> Logger.w(error) { "Phone location updates stopped; waiting for reconciliation" } }
                    .launchIn(currentScope)
        }
    }

    override fun restart() {
        synchronized(stateLock) { startLocked() }
    }

    override fun setLocationAccessAllowed(allowed: Boolean) {
        synchronized(stateLock) {
            if (locationAccessAllowed == allowed) {
                if (allowed) startLocked()
                return
            }

            locationAccessAllowed = allowed
            if (allowed) {
                startLocked()
            } else {
                stopLocationUpdatesLocked()
            }
        }
    }

    override fun stop() {
        synchronized(stateLock) {
            stopLocationUpdatesLocked()
            sendPositionFn = null
        }
    }

    private fun stopLocationUpdatesLocked() {
        if (locationFlow != null) Logger.i { "Stopping location requests" }
        locationFlow?.cancel()
        locationFlow = null
    }
}
