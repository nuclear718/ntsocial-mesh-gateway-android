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
@file:Suppress("TooManyFunctions")

package com.ntsocial.meshlink.feature.node.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.env_metrics_log
import com.ntsocial.meshlink.core.resources.hardware_model
import com.ntsocial.meshlink.core.resources.humidity
import com.ntsocial.meshlink.core.resources.iaq
import com.ntsocial.meshlink.core.resources.node_id
import com.ntsocial.meshlink.core.resources.pax
import com.ntsocial.meshlink.core.resources.pax_metrics_log
import com.ntsocial.meshlink.core.resources.role
import com.ntsocial.meshlink.core.resources.soil_moisture
import com.ntsocial.meshlink.core.resources.soil_temperature
import com.ntsocial.meshlink.core.resources.temperature
import com.ntsocial.meshlink.core.ui.icon.AirQuality
import com.ntsocial.meshlink.core.ui.icon.ElectricPower
import com.ntsocial.meshlink.core.ui.icon.HardwareModel
import com.ntsocial.meshlink.core.ui.icon.Humidity
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.NodeId
import com.ntsocial.meshlink.core.ui.icon.PeopleCount
import com.ntsocial.meshlink.core.ui.icon.Role
import com.ntsocial.meshlink.core.ui.icon.SoilMoisture
import com.ntsocial.meshlink.core.ui.icon.Temperature
import org.jetbrains.compose.resources.stringResource

@Composable
fun TemperatureInfo(
    temp: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Temperature,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.temperature),
        text = temp,
        contentColor = contentColor,
    )
}

@Composable
fun HumidityInfo(
    humidity: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Humidity,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.humidity),
        text = humidity,
        contentColor = contentColor,
    )
}

@Composable
fun SoilTemperatureInfo(
    temp: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.SoilMoisture,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.soil_temperature),
        text = temp,
        contentColor = contentColor,
    )
}

@Composable
fun SoilMoistureInfo(
    moisture: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.SoilMoisture,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.soil_moisture),
        text = moisture,
        contentColor = contentColor,
    )
}

@Composable
fun PaxcountInfo(
    pax: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.PeopleCount,
        contentDescription = stringResource(Res.string.pax_metrics_log),
        label = stringResource(Res.string.pax),
        text = pax,
        contentColor = contentColor,
    )
}

@Composable
fun AirQualityInfo(
    iaq: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.AirQuality,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.iaq),
        text = iaq,
        contentColor = contentColor,
    )
}

@Composable
fun PowerInfo(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.ElectricPower,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = label,
        text = value,
        contentColor = contentColor,
    )
}

@Composable
fun HardwareInfo(
    hwModel: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.HardwareModel,
        contentDescription = stringResource(Res.string.hardware_model),
        text = hwModel,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun RoleInfo(role: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Role,
        contentDescription = stringResource(Res.string.role),
        text = role,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun NodeIdInfo(id: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.NodeId,
        contentDescription = stringResource(Res.string.node_id),
        text = id,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}
