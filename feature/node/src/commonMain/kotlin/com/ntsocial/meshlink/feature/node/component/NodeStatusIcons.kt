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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.connected
import com.ntsocial.meshlink.core.resources.connecting
import com.ntsocial.meshlink.core.resources.device_sleeping
import com.ntsocial.meshlink.core.resources.disconnected
import com.ntsocial.meshlink.core.resources.favorite
import com.ntsocial.meshlink.core.resources.mute_always
import com.ntsocial.meshlink.core.resources.unmessageable
import com.ntsocial.meshlink.core.resources.unmonitored_or_infrastructure
import com.ntsocial.meshlink.core.ui.component.ConnectionsNavIcon
import com.ntsocial.meshlink.core.ui.component.NodeSignedStatusIcon
import com.ntsocial.meshlink.core.ui.icon.Favorite
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Unmessageable
import com.ntsocial.meshlink.core.ui.icon.VolumeOff
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusYellow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeStatusIcons(
    isThisNode: Boolean,
    isUnmessageable: Boolean,
    isFavorite: Boolean,
    isMuted: Boolean,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
    signsPackets: Boolean = false,
    deviceType: DeviceType? = null,
    contentColor: Color = LocalContentColor.current,
) {
    Row(modifier = modifier.padding(4.dp)) {
        if (isThisNode) {
            ThisNodeStatusBadge(connectionState = connectionState, deviceType = deviceType)
        }

        if (isUnmessageable) {
            StatusBadge(
                imageVector = MeshtasticIcons.Unmessageable,
                contentDescription = Res.string.unmessageable,
                tooltipText = Res.string.unmonitored_or_infrastructure,
                tint = contentColor,
            )
        }
        if (isMuted && !isThisNode) {
            StatusBadge(
                imageVector = MeshtasticIcons.VolumeOff,
                contentDescription = Res.string.mute_always,
                tooltipText = Res.string.mute_always,
                tint = contentColor,
            )
        }
        if (isFavorite && !isThisNode) {
            StatusBadge(
                imageVector = MeshtasticIcons.Favorite,
                contentDescription = Res.string.favorite,
                tooltipText = Res.string.favorite,
                tint = MaterialTheme.colorScheme.StatusYellow,
            )
        }
        if (signsPackets) {
            NodeSignedStatusIcon(modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThisNodeStatusBadge(connectionState: ConnectionState, deviceType: DeviceType?) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(
                    stringResource(
                        when (connectionState) {
                            ConnectionState.Connected -> Res.string.connected
                            ConnectionState.Connecting -> Res.string.connecting
                            ConnectionState.Disconnected -> Res.string.disconnected
                            ConnectionState.DeviceSleep -> Res.string.device_sleeping
                        },
                    ),
                )
            }
        },
        state = rememberTooltipState(),
    ) {
        ConnectionsNavIcon(connectionState = connectionState, deviceType = deviceType, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusBadge(
    imageVector: ImageVector,
    contentDescription: StringResource,
    tooltipText: StringResource,
    tint: Color = LocalContentColor.current,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(tooltipText)) } },
        state = rememberTooltipState(),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = stringResource(contentDescription),
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}
