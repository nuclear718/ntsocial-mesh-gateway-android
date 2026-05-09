/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.hops_away
import com.ntsocial.meshlink.core.ui.icon.HopCount
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun HopsInfo(hops: Int, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.HopCount,
        contentDescription = stringResource(Res.string.hops_away),
        label = stringResource(Res.string.hops_away),
        text = hops.toString(),
        contentColor = contentColor,
    )
}

@PreviewLightDark
@Composable
private fun HopsInfoPreview() {
    AppTheme { HopsInfo(hops = 3) }
}
