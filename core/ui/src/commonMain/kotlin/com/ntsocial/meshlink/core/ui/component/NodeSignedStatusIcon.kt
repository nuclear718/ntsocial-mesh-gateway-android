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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.security_icon_help_dismiss
import com.ntsocial.meshlink.core.resources.security_signed_node
import com.ntsocial.meshlink.core.resources.security_signed_node_help
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.ShieldCheck
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusGreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun SignedNodeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.security_signed_node)) },
        text = { Text(stringResource(Res.string.security_signed_node_help)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.security_icon_help_dismiss)) }
        },
    )
}

@Composable
fun NodeSignedStatusIcon(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) SignedNodeDialog(onDismiss = { showDialog = false })
    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            imageVector = MeshtasticIcons.ShieldCheck,
            contentDescription = stringResource(Res.string.security_signed_node),
            tint = MaterialTheme.colorScheme.StatusGreen,
        )
    }
}
