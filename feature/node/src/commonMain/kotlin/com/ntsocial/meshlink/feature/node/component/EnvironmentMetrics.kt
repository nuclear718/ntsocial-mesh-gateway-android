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
package com.ntsocial.meshlink.feature.node.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.common.util.NumberFormatter
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.util.UnitConversions
import com.ntsocial.meshlink.core.model.util.UnitConversions.toTempString
import com.ntsocial.meshlink.core.model.util.toSmallDistanceString
import com.ntsocial.meshlink.core.model.util.toSpeedString
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.current
import com.ntsocial.meshlink.core.resources.dew_point
import com.ntsocial.meshlink.core.resources.distance
import com.ntsocial.meshlink.core.resources.gas_resistance
import com.ntsocial.meshlink.core.resources.humidity
import com.ntsocial.meshlink.core.resources.iaq
import com.ntsocial.meshlink.core.resources.ic_dew_point
import com.ntsocial.meshlink.core.resources.ic_radioactive
import com.ntsocial.meshlink.core.resources.ic_soil_moisture
import com.ntsocial.meshlink.core.resources.ic_soil_temperature
import com.ntsocial.meshlink.core.resources.lux
import com.ntsocial.meshlink.core.resources.one_wire_temperature
import com.ntsocial.meshlink.core.resources.pressure
import com.ntsocial.meshlink.core.resources.radiation
import com.ntsocial.meshlink.core.resources.soil_moisture
import com.ntsocial.meshlink.core.resources.soil_temperature
import com.ntsocial.meshlink.core.resources.temperature
import com.ntsocial.meshlink.core.resources.uv_lux
import com.ntsocial.meshlink.core.resources.voltage
import com.ntsocial.meshlink.core.resources.weight
import com.ntsocial.meshlink.core.resources.wind
import com.ntsocial.meshlink.core.ui.icon.AirQuality
import com.ntsocial.meshlink.core.ui.icon.Altitude
import com.ntsocial.meshlink.core.ui.icon.Humidity
import com.ntsocial.meshlink.core.ui.icon.LightMode
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Particulate
import com.ntsocial.meshlink.core.ui.icon.PowerSupply
import com.ntsocial.meshlink.core.ui.icon.Pressure
import com.ntsocial.meshlink.core.ui.icon.Temperature
import com.ntsocial.meshlink.core.ui.icon.Voltage
import com.ntsocial.meshlink.core.ui.icon.Weight
import com.ntsocial.meshlink.core.ui.icon.WindDirection
import com.ntsocial.meshlink.feature.node.model.DrawableMetricInfo
import com.ntsocial.meshlink.feature.node.model.VectorMetricInfo
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun EnvironmentMetrics(
    node: Node,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean = false,
) {
    val vectorMetrics = buildList {
        with(node.environmentMetrics) {
            temperature?.let { temp ->
                if (!temp.isNaN()) {
                    add(
                        VectorMetricInfo(
                            label = Res.string.temperature,
                            value = temp.toTempString(isFahrenheit),
                            icon = MeshtasticIcons.Temperature,
                        ),
                    )
                }
            }
            relative_humidity?.let { rh ->
                add(
                    VectorMetricInfo(
                        label = Res.string.humidity,
                        value = "${NumberFormatter.format(rh, 0)}%",
                        icon = MeshtasticIcons.Humidity,
                    ),
                )
            }
            barometric_pressure?.let { bp ->
                add(
                    VectorMetricInfo(
                        label = Res.string.pressure,
                        value = "${NumberFormatter.format(bp, 0)} hPa",
                        icon = MeshtasticIcons.Pressure,
                    ),
                )
            }
            gas_resistance?.let { gr ->
                add(
                    VectorMetricInfo(
                        label = Res.string.gas_resistance,
                        value = "${NumberFormatter.format(gr, 0)} MΩ",
                        icon = MeshtasticIcons.Particulate,
                    ),
                )
            }
            voltage?.let { v ->
                add(
                    VectorMetricInfo(
                        label = Res.string.voltage,
                        value = "${NumberFormatter.format(v, 2)}V",
                        icon = MeshtasticIcons.Voltage,
                    ),
                )
            }
            current?.let { c ->
                add(
                    VectorMetricInfo(
                        label = Res.string.current,
                        value = "${NumberFormatter.format(c, 1)}mA",
                        icon = MeshtasticIcons.PowerSupply,
                    ),
                )
            }
            iaq?.let { i ->
                add(VectorMetricInfo(label = Res.string.iaq, value = i.toString(), icon = MeshtasticIcons.AirQuality))
            }
            distance?.let { d ->
                add(
                    VectorMetricInfo(
                        label = Res.string.distance,
                        value = d.toSmallDistanceString(displayUnits),
                        icon = MeshtasticIcons.Altitude,
                    ),
                )
            }
            lux?.let { l ->
                add(
                    VectorMetricInfo(
                        label = Res.string.lux,
                        value = "${NumberFormatter.format(l, 0)} lx",
                        icon = MeshtasticIcons.LightMode,
                    ),
                )
            }
            uv_lux?.let { uvl ->
                add(
                    VectorMetricInfo(
                        label = Res.string.uv_lux,
                        value = "${NumberFormatter.format(uvl, 0)} lx",
                        icon = MeshtasticIcons.LightMode,
                    ),
                )
            }
            wind_speed?.let { ws ->
                @Suppress("MagicNumber")
                val normalizedBearing = ((wind_direction ?: 0) + 180) % 360
                add(
                    VectorMetricInfo(
                        label = Res.string.wind,
                        value = ws.toSpeedString(displayUnits),
                        icon = MeshtasticIcons.WindDirection,
                        rotateIcon = normalizedBearing.toFloat(),
                    ),
                )
            }
            weight?.let { w ->
                add(
                    VectorMetricInfo(
                        label = Res.string.weight,
                        value = "${NumberFormatter.format(w, 2)} kg",
                        icon = MeshtasticIcons.Weight,
                    ),
                )
            }
            if (temperature != null && relative_humidity != null) {
                val dewPoint = UnitConversions.calculateDewPoint(temperature!!, relative_humidity!!)
                if (!dewPoint.isNaN()) {
                    add(
                        DrawableMetricInfo(
                            label = Res.string.dew_point,
                            value = dewPoint.toTempString(isFahrenheit),
                            icon = Res.drawable.ic_dew_point,
                        ),
                    )
                }
            }
            soil_temperature?.let { st ->
                if (!st.isNaN()) {
                    add(
                        DrawableMetricInfo(
                            label = Res.string.soil_temperature,
                            value = st.toTempString(isFahrenheit),
                            icon = Res.drawable.ic_soil_temperature,
                        ),
                    )
                }
            }
            soil_moisture?.let { sm ->
                add(
                    DrawableMetricInfo(
                        label = Res.string.soil_moisture,
                        value = "$sm%",
                        icon = Res.drawable.ic_soil_moisture,
                    ),
                )
            }
            radiation?.let { r ->
                add(
                    DrawableMetricInfo(
                        label = Res.string.radiation,
                        value = "${NumberFormatter.format(r, 1)} µR/h",
                        icon = Res.drawable.ic_radioactive,
                    ),
                )
            }
            // 1-Wire temperature sensors (up to 8 channels)
            one_wire_temperature
                .filterNot { it.isNaN() }
                .forEachIndexed { idx, temp ->
                    add(
                        DrawableMetricInfo(
                            label = Res.string.one_wire_temperature,
                            value = "${idx + 1}: ${temp.toTempString(isFahrenheit)}",
                            icon = Res.drawable.ic_soil_temperature,
                        ),
                    )
                }
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        vectorMetrics.forEach { metric ->
            if (metric is DrawableMetricInfo) {
                DrawableInfoCard(
                    iconRes = metric.icon,
                    text = stringResource(metric.label),
                    value = metric.value,
                    rotateIcon = metric.rotateIcon,
                )
            } else if (metric is VectorMetricInfo) {
                InfoCard(
                    icon = metric.icon,
                    text = stringResource(metric.label),
                    value = metric.value,
                    rotateIcon = metric.rotateIcon,
                )
            }
        }
    }
}
