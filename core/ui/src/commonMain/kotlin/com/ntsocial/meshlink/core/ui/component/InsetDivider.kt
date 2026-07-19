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
package com.ntsocial.meshlink.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun InsetDivider(
    modifier: Modifier = Modifier,
    inset: Dp = 16.dp,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    InsetDivider(modifier = modifier, startInset = inset, endInset = inset, thickness = thickness, color = color)
}

@Composable
fun InsetDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset, end = endInset),
        thickness = thickness,
        color = color,
    )
}
