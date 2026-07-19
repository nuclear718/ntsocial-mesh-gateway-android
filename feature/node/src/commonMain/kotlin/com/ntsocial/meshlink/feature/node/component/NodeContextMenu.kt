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

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.add_favorite
import com.ntsocial.meshlink.core.resources.ignore
import com.ntsocial.meshlink.core.resources.mute_always
import com.ntsocial.meshlink.core.resources.remove
import com.ntsocial.meshlink.core.resources.remove_favorite
import com.ntsocial.meshlink.core.resources.remove_ignored
import com.ntsocial.meshlink.core.resources.unmute
import com.ntsocial.meshlink.core.ui.icon.DeleteNode
import com.ntsocial.meshlink.core.ui.icon.DoDisturb
import com.ntsocial.meshlink.core.ui.icon.Favorite
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.NotFavorite
import com.ntsocial.meshlink.core.ui.icon.VolumeOff
import com.ntsocial.meshlink.core.ui.icon.VolumeUp
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusRed
import org.jetbrains.compose.resources.stringResource

/**
 * Shared context menu for node actions (favorite, ignore, mute, remove).
 *
 * Used by both Android and Desktop adaptive node list screens.
 */
@Composable
fun NodeContextMenu(
    expanded: Boolean,
    node: Node,
    onFavorite: () -> Unit,
    onIgnore: () -> Unit,
    onMute: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FavoriteMenuItem(node, onFavorite, onDismiss)
        IgnoreMenuItem(node, onIgnore, onDismiss)
        if (node.capabilities.canMuteNode) {
            MuteMenuItem(node, onMute, onDismiss)
        }
        RemoveMenuItem(node, onRemove, onDismiss)
    }
}

@Composable
private fun FavoriteMenuItem(node: Node, onFavorite: () -> Unit, onDismiss: () -> Unit) {
    val isFavorite = node.isFavorite
    DropdownMenuItem(
        onClick = {
            onFavorite()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = {
            Icon(
                imageVector = if (isFavorite) MeshtasticIcons.Favorite else MeshtasticIcons.NotFavorite,
                contentDescription = null,
            )
        },
        text = { Text(stringResource(if (isFavorite) Res.string.remove_favorite else Res.string.add_favorite)) },
    )
}

@Composable
private fun IgnoreMenuItem(node: Node, onIgnore: () -> Unit, onDismiss: () -> Unit) {
    val isIgnored = node.isIgnored
    DropdownMenuItem(
        onClick = {
            onIgnore()
            onDismiss()
        },
        leadingIcon = {
            Icon(
                imageVector = if (isIgnored) MeshtasticIcons.DoDisturb else MeshtasticIcons.DoDisturb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.StatusRed,
            )
        },
        text = {
            Text(
                text = stringResource(if (isIgnored) Res.string.remove_ignored else Res.string.ignore),
                color = MaterialTheme.colorScheme.StatusRed,
            )
        },
    )
}

@Composable
private fun MuteMenuItem(node: Node, onMute: () -> Unit, onDismiss: () -> Unit) {
    val isMuted = node.isMuted
    DropdownMenuItem(
        onClick = {
            onMute()
            onDismiss()
        },
        leadingIcon = {
            Icon(
                imageVector = if (isMuted) MeshtasticIcons.VolumeOff else MeshtasticIcons.VolumeUp,
                contentDescription = null,
            )
        },
        text = { Text(text = stringResource(if (isMuted) Res.string.unmute else Res.string.mute_always)) },
    )
}

@Composable
private fun RemoveMenuItem(node: Node, onRemove: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(
        onClick = {
            onRemove()
            onDismiss()
        },
        enabled = !node.isIgnored,
        leadingIcon = {
            Icon(
                imageVector = MeshtasticIcons.DeleteNode,
                contentDescription = null,
                tint = if (node.isIgnored) LocalContentColor.current else MaterialTheme.colorScheme.StatusRed,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.remove),
                color = if (node.isIgnored) Color.Unspecified else MaterialTheme.colorScheme.StatusRed,
            )
        },
    )
}
