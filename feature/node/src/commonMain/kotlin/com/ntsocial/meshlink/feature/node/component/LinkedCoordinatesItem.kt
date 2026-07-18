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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.ntsocial.meshlink.core.common.util.GPSFormat
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.util.metersIn
import com.ntsocial.meshlink.core.model.util.toString
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.copy
import com.ntsocial.meshlink.core.resources.elevation_suffix
import com.ntsocial.meshlink.core.resources.last_position_update
import com.ntsocial.meshlink.core.ui.component.BasicListItem
import com.ntsocial.meshlink.core.ui.component.icon
import com.ntsocial.meshlink.core.ui.icon.Copy
import com.ntsocial.meshlink.core.ui.icon.LocationOn
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.util.createClipEntry
import com.ntsocial.meshlink.core.ui.util.formatAgo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkedCoordinatesItem(
    node: Node,
    displayUnits: Config.DisplayConfig.DisplayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
) {
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val ago = formatAgo(node.position.time)
    val coordinates = GPSFormat.toDec(node.latitude, node.longitude)
    val elevationText =
        node.validPosition?.altitude?.let { altitude ->
            val suffix = stringResource(Res.string.elevation_suffix)
            " • ${altitude.metersIn(displayUnits).toString(displayUnits)} $suffix"
        } ?: ""

    val copyLabel = stringResource(Res.string.copy)

    BasicListItem(
        modifier =
        Modifier.semantics {
            role = Role.Button
            customActions =
                listOf(
                    CustomAccessibilityAction(copyLabel) {
                        coroutineScope.launch { clipboard.setClipEntry(createClipEntry(coordinates, copyLabel)) }
                        true
                    },
                )
        },
        text = stringResource(Res.string.last_position_update),
        leadingIcon = MeshtasticIcons.LocationOn,
        supportingText = "$ago • $coordinates$elevationText",
        trailingContent = MeshtasticIcons.Copy.icon(),
        onClick = { coroutineScope.launch { clipboard.setClipEntry(createClipEntry(coordinates, copyLabel)) } },
        onLongClick = { coroutineScope.launch { clipboard.setClipEntry(createClipEntry(coordinates, copyLabel)) } },
    )
}
