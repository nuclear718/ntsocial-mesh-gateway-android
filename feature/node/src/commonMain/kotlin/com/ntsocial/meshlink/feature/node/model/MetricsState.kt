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
package com.ntsocial.meshlink.feature.node.model

import com.ntsocial.meshlink.core.database.entity.FirmwareRelease
import com.ntsocial.meshlink.core.model.DeviceHardware
import com.ntsocial.meshlink.core.model.DeviceLink
import com.ntsocial.meshlink.core.model.MeshLog
import com.ntsocial.meshlink.core.model.Node
import org.meshtastic.proto.Config
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry

data class MetricsState(
    val isLocal: Boolean = false,
    val isManaged: Boolean = true,
    val isFahrenheit: Boolean = false,
    val displayUnits: Config.DisplayConfig.DisplayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
    val node: Node? = null,
    val deviceMetrics: List<Telemetry> = emptyList(),
    val signalMetrics: List<MeshPacket> = emptyList(),
    val powerMetrics: List<Telemetry> = emptyList(),
    val airQualityMetrics: List<Telemetry> = emptyList(),
    val hostMetrics: List<Telemetry> = emptyList(),
    val tracerouteRequests: List<MeshLog> = emptyList(),
    val tracerouteResults: List<MeshLog> = emptyList(),
    val neighborInfoRequests: List<MeshLog> = emptyList(),
    val neighborInfoResults: List<MeshLog> = emptyList(),
    val positionLogs: List<Position> = emptyList(),
    val deviceHardware: DeviceHardware? = null,
    val deviceLinks: List<DeviceLink> = emptyList(),
    val firmwareEdition: FirmwareEdition? = null,
    val latestStableFirmware: FirmwareRelease = FirmwareRelease(),
    val latestAlphaFirmware: FirmwareRelease = FirmwareRelease(),
    val paxMetrics: List<MeshLog> = emptyList(),
    /** The PlatformIO environment reported by the device (if known). */
    val reportedTarget: String? = null,
) {
    fun hasDeviceMetrics() = deviceMetrics.isNotEmpty()

    fun hasSignalMetrics() = signalMetrics.isNotEmpty()

    fun hasPowerMetrics() = powerMetrics.isNotEmpty()

    fun hasAirQualityMetrics() = airQualityMetrics.isNotEmpty()

    fun hasTracerouteLogs() = tracerouteRequests.isNotEmpty()

    fun hasNeighborInfoLogs() = neighborInfoRequests.isNotEmpty()

    fun hasPositionLogs() = positionLogs.isNotEmpty()

    fun hasHostMetrics() = hostMetrics.isNotEmpty()

    fun hasPaxMetrics() = paxMetrics.isNotEmpty()

    /** Finds the oldest timestamp (in seconds) among all collected metric types. */
    @Suppress("MagicNumber")
    fun oldestTimestampSeconds(): Long? {
        val telemetryTimes = (deviceMetrics + powerMetrics + airQualityMetrics + hostMetrics).map { it.time.toLong() }
        val signalTimes = signalMetrics.map { it.rx_time.toLong() }
        val logTimes =
            (tracerouteRequests + tracerouteResults + neighborInfoRequests + neighborInfoResults + paxMetrics).map {
                it.received_date / 1000L
            }
        val positionTimes = positionLogs.map { it.time.toLong() }

        val allTimes = telemetryTimes + signalTimes + logTimes + positionTimes
        return allTimes.minOrNull()
    }

    companion object {
        val Empty = MetricsState()
    }
}
