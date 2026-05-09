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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.actions
import com.ntsocial.meshlink.core.resources.direct_message
import com.ntsocial.meshlink.core.resources.favorite
import com.ntsocial.meshlink.core.resources.ignore
import com.ntsocial.meshlink.core.resources.mute_notifications
import com.ntsocial.meshlink.core.resources.remove
import com.ntsocial.meshlink.core.resources.share_contact
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.component.SwitchListItem
import com.ntsocial.meshlink.core.ui.icon.Delete
import com.ntsocial.meshlink.core.ui.icon.Favorite
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Message
import com.ntsocial.meshlink.core.ui.icon.NotFavorite
import com.ntsocial.meshlink.core.ui.icon.QrCode2
import com.ntsocial.meshlink.core.ui.icon.VolumeMute
import com.ntsocial.meshlink.core.ui.icon.VolumeOff
import com.ntsocial.meshlink.core.ui.icon.VolumeUp
import com.ntsocial.meshlink.feature.node.model.LogsType
import com.ntsocial.meshlink.feature.node.model.NodeDetailAction
import com.ntsocial.meshlink.feature.node.model.isEffectivelyUnmessageable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.Config

@Composable
fun DeviceActions(
    node: Node,
    ourNode: Node?,
    lastTracerouteTime: Long?,
    lastRequestNeighborsTime: Long?,
    availableLogs: Set<LogsType>,
    onAction: (NodeDetailAction) -> Unit,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = Res.string.actions) {
            PrimaryActionsRow(node = node, isLocal = isLocal, onAction = onAction)

            if (!isLocal) {
                SectionDivider(Modifier.padding(vertical = 8.dp))
                ManagementActions(node = node, onAction = onAction)
            }
        }

        TelemetricActionsSection(
            node = node,
            ourNode = ourNode,
            availableLogs = availableLogs,
            lastTracerouteTime = lastTracerouteTime,
            lastRequestNeighborsTime = lastRequestNeighborsTime,
            displayUnits = displayUnits,
            isFahrenheit = isFahrenheit,
            onAction = onAction,
            isLocal = isLocal,
        )
    }
}

@Composable
private fun PrimaryActionsRow(node: Node, isLocal: Boolean, onAction: (NodeDetailAction) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!node.isEffectivelyUnmessageable && !isLocal) {
            Button(
                onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.DirectMessage(node))) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(MeshtasticIcons.Message, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.direct_message))
            }
        }

        OutlinedButton(
            onClick = { onAction(NodeDetailAction.ShareContact) },
            modifier = if (node.isEffectivelyUnmessageable || isLocal) Modifier.weight(1f) else Modifier,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(MeshtasticIcons.QrCode2, contentDescription = null)
            if (node.isEffectivelyUnmessageable || isLocal) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_contact))
            }
        }

        if (!isLocal) {
            IconToggleButton(
                checked = node.isFavorite,
                onCheckedChange = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Favorite(node))) },
            ) {
                Icon(
                    imageVector = if (node.isFavorite) MeshtasticIcons.Favorite else MeshtasticIcons.NotFavorite,
                    contentDescription = stringResource(Res.string.favorite),
                    tint = if (node.isFavorite) Color.Yellow else LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
private fun ManagementActions(node: Node, onAction: (NodeDetailAction) -> Unit) {
    Column {
        SwitchListItem(
            text = stringResource(Res.string.ignore),
            leadingIcon =
            if (node.isIgnored) {
                MeshtasticIcons.VolumeMute
            } else {
                MeshtasticIcons.VolumeUp
            },
            checked = node.isIgnored,
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Ignore(node))) },
        )

        if (node.capabilities.canMuteNode) {
            SwitchListItem(
                text = stringResource(Res.string.mute_notifications),
                leadingIcon =
                if (node.isMuted) {
                    MeshtasticIcons.VolumeOff
                } else {
                    MeshtasticIcons.VolumeUp
                },
                checked = node.isMuted,
                onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Mute(node))) },
            )
        }

        ListItem(
            text = stringResource(Res.string.remove),
            leadingIcon = MeshtasticIcons.Delete,
            trailingIcon = null,
            textColor = MaterialTheme.colorScheme.error,
            leadingIconTint = MaterialTheme.colorScheme.error,
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Remove(node))) },
        )
    }
}
