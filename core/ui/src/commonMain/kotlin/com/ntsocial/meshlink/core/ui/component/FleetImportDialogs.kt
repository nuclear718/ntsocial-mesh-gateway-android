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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.navigation.EndpointTarget
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.radio_import_target
import com.ntsocial.meshlink.core.ui.qr.ScannedQrCodeDialog
import com.ntsocial.meshlink.core.ui.share.SharedContactDialog
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import org.jetbrains.compose.resources.stringResource

/** External URLs contain no radio owner. Require an explicit target before creating a preview or command. */
@Composable
fun FleetImportDialogs(
    viewModel: UIViewModel,
    options: List<EndpointImportOption>,
    endpointContent: @Composable (EndpointTarget, @Composable () -> Unit) -> Unit,
) {
    val channelSet by viewModel.requestChannelSet.collectAsStateWithLifecycle()
    val contact by viewModel.sharedContactRequested.collectAsStateWithLifecycle()
    var target by remember(channelSet, contact) { mutableStateOf<EndpointTarget?>(null) }
    if (channelSet == null && contact == null) return
    val dismiss = {
        viewModel.clearRequestChannelUrl()
        viewModel.clearSharedContactRequested()
    }
    val selectedTarget = target?.takeIf { candidate -> options.any { it.target == candidate && it.ready } }
    LaunchedEffect(selectedTarget) { if (selectedTarget == null) target = null }
    if (selectedTarget == null) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(Res.string.radio_import_target)) },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(onClick = { target = option.target }, enabled = option.ready) { Text(option.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    } else {
        endpointContent(selectedTarget) {
            channelSet?.let { ScannedQrCodeDialog(it, onDismiss = dismiss) }
            contact?.let { SharedContactDialog(sharedContact = it, onDismiss = dismiss) }
        }
    }
}
