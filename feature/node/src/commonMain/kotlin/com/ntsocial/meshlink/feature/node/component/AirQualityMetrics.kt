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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.co2
import com.ntsocial.meshlink.core.resources.micrograms_per_cubic_meter
import com.ntsocial.meshlink.core.resources.pm10
import com.ntsocial.meshlink.core.resources.pm1_0
import com.ntsocial.meshlink.core.resources.pm2_5
import com.ntsocial.meshlink.core.resources.ppm
import com.ntsocial.meshlink.core.ui.component.Co2Severity
import com.ntsocial.meshlink.core.ui.icon.AirQuality
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.feature.node.model.VectorMetricInfo
import org.jetbrains.compose.resources.stringResource

/**
 * Displays air quality info cards for a node. A present reading of 0 is valid and is shown; only absent metrics are
 * hidden.
 */
@Composable
internal fun AirQualityInfoCards(node: Node) {
    val metrics = node.airQualityMetrics
    val ugm3 = stringResource(Res.string.micrograms_per_cubic_meter)
    val ppmUnit = stringResource(Res.string.ppm)

    val cards = buildList {
        metrics.pm10_standard?.let { pm ->
            add(VectorMetricInfo(Res.string.pm1_0, "$pm $ugm3", MeshtasticIcons.AirQuality))
        }
        metrics.pm25_standard?.let { pm ->
            add(VectorMetricInfo(Res.string.pm2_5, "$pm $ugm3", MeshtasticIcons.AirQuality))
        }
        metrics.pm100_standard?.let { pm ->
            add(VectorMetricInfo(Res.string.pm10, "$pm $ugm3", MeshtasticIcons.AirQuality))
        }
        metrics.co2?.let { co2 -> add(VectorMetricInfo(Res.string.co2, "$co2 $ppmUnit", MeshtasticIcons.AirQuality)) }
    }

    if (cards.isEmpty()) return

    val co2Value = metrics.co2 ?: 0
    val co2Color = Co2Severity.fromPpm(co2Value)?.color

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        cards.forEach { metric ->
            val valueColor =
                if (metric.label == Res.string.co2 && co2Color != null) {
                    co2Color
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            InfoCard(
                icon = metric.icon,
                text = stringResource(metric.label),
                value = metric.value,
                valueColor = valueColor,
            )
        }
    }
}
