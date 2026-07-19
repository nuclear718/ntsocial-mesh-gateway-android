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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.util.toDistanceString
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.open_compass
import com.ntsocial.meshlink.core.ui.icon.Compass
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.feature.node.model.NodeDetailAction
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

/**
 * Inline position content shown beneath the Position row in the Telemetry section. Displays distance, copyable
 * coordinates, and the local compass without embedding or launching a map.
 */
@Composable
internal fun PositionInlineContent(
    node: Node,
    ourNode: Node?,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    onAction: (NodeDetailAction) -> Unit,
) {
    val distance = ourNode?.distance(node)?.takeIf { it > 0 }?.toDistanceString(displayUnits)

    distance?.let {
        DistanceInfo(distance = it)
        Spacer(Modifier.height(8.dp))
    }
    LinkedCoordinatesItem(node, displayUnits)
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(
        onClick = { onAction(NodeDetailAction.OpenCompass(node, displayUnits)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(MeshtasticIcons.Compass, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.open_compass),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
