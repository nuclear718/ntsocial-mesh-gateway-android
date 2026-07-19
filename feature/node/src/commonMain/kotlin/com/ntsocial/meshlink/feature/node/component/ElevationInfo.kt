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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ntsocial.meshlink.core.model.util.metersIn
import com.ntsocial.meshlink.core.model.util.toString
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.altitude
import com.ntsocial.meshlink.core.resources.elevation_suffix
import com.ntsocial.meshlink.core.ui.icon.Elevation
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@Composable
fun ElevationInfo(
    modifier: Modifier = Modifier,
    altitude: Int,
    system: Config.DisplayConfig.DisplayUnits,
    suffix: String = stringResource(Res.string.elevation_suffix),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Elevation,
        contentDescription = stringResource(Res.string.altitude),
        label = stringResource(Res.string.altitude),
        text = altitude.metersIn(system).toString(system) + " " + suffix,
        contentColor = contentColor,
    )
}
