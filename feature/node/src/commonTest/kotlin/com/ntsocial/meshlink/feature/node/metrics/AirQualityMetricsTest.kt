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
package com.ntsocial.meshlink.feature.node.metrics

import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AirQualityMetricsTest {

    private fun telemetry(aq: AirQualityMetrics) = Telemetry(air_quality_metrics = aq)

    @Test
    fun `getValue returns present non-zero readings`() {
        val t = telemetry(AirQualityMetrics(pm10_standard = 1, pm25_standard = 2, pm100_standard = 3, co2 = 800))
        assertEquals(1f, AirQuality.PM1_0.getValue(t))
        assertEquals(2f, AirQuality.PM2_5.getValue(t))
        assertEquals(3f, AirQuality.PM10.getValue(t))
        assertEquals(800f, AirQuality.CO2.getValue(t))
    }

    @Test
    fun `getValue plots a present-zero reading instead of suppressing it`() {
        val t = telemetry(AirQualityMetrics(pm10_standard = 0, pm25_standard = 0, pm100_standard = 0, co2 = 0))
        assertEquals(0f, AirQuality.PM1_0.getValue(t))
        assertEquals(0f, AirQuality.PM2_5.getValue(t))
        assertEquals(0f, AirQuality.PM10.getValue(t))
        assertEquals(0f, AirQuality.CO2.getValue(t))
    }

    @Test
    fun `getValue returns null for an absent field so a partial-sensor node does not chart spurious series`() {
        val t = telemetry(AirQualityMetrics(co2 = 450))
        assertNull(AirQuality.PM1_0.getValue(t))
        assertNull(AirQuality.PM2_5.getValue(t))
        assertNull(AirQuality.PM10.getValue(t))
        assertEquals(450f, AirQuality.CO2.getValue(t))
    }

    @Test
    fun `getValue returns null for every series when there are no air quality metrics`() {
        val t = Telemetry()
        AirQuality.entries.forEach {
            assertNull(it.getValue(t), "${it.name} should be null without air_quality_metrics")
        }
    }
}
