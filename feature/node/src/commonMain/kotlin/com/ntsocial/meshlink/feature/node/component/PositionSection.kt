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
package com.ntsocial.meshlink.feature.node.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.util.toDistanceString
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.open_compass
import com.ntsocial.meshlink.core.ui.icon.Compass
import com.ntsocial.meshlink.core.ui.icon.Distance
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.util.LocalInlineMapProvider
import com.ntsocial.meshlink.feature.node.model.NodeDetailAction
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

private const val MAP_HEIGHT_DP = 200

/**
 * Inline position content shown beneath the Position row in the Telemetry section. Displays the inline map with
 * distance badge, linked coordinates, and compass button.
 */
@Composable
internal fun PositionInlineContent(
    node: Node,
    ourNode: Node?,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    onAction: (NodeDetailAction) -> Unit,
) {
    val distance = ourNode?.distance(node)?.takeIf { it > 0 }?.toDistanceString(displayUnits)

    PositionMap(node, distance)
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

@Composable
private fun PositionMap(node: Node, distance: String?) {
    Box(modifier = Modifier.padding(vertical = 4.dp)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(MAP_HEIGHT_DP.dp)) {
            LocalInlineMapProvider.current(node, Modifier.fillMaxSize())
        }
        if (distance != null && distance.isNotEmpty()) {
            Surface(
                modifier = Modifier.padding(12.dp).align(Alignment.TopEnd),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MeshtasticIcons.Distance, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(distance, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
