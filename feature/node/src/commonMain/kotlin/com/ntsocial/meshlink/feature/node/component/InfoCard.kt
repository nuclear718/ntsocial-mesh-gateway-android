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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.a11y_label_value
import com.ntsocial.meshlink.core.resources.copy
import com.ntsocial.meshlink.core.ui.util.createClipEntry
import com.ntsocial.meshlink.core.ui.util.thenIf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoCard(
    text: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: DrawableResource? = null,
    rotateIcon: Float = 0f,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val shape = MaterialTheme.shapes.medium
    val copyLabel = stringResource(Res.string.copy)
    val contentDescriptionText = stringResource(Res.string.a11y_label_value, text, value)

    Card(
        modifier =
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .combinedClickable(
                onLongClick = { coroutineScope.launch { clipboard.setClipEntry(createClipEntry(value, text)) } },
                onLongClickLabel = copyLabel,
                onClick = {},
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) { contentDescription = contentDescriptionText },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconModifier = Modifier.size(20.dp).thenIf(rotateIcon != 0f) { rotate(rotateIcon) }
            val iconTint = MaterialTheme.colorScheme.primary
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = iconModifier, tint = iconTint)
            }
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = iconTint,
                )
            }
            Column {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
            }
        }
    }
}

@Composable
internal fun DrawableInfoCard(iconRes: DrawableResource, text: String, value: String, rotateIcon: Float = 0f) {
    InfoCard(iconRes = iconRes, text = text, value = value, rotateIcon = rotateIcon)
}
