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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.repository.CHANNEL_APPLY_RESTART_SECONDS
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_apply_in_progress
import com.ntsocial.meshlink.core.resources.channel_apply_invalid
import com.ntsocial.meshlink.core.resources.channel_apply_pending
import com.ntsocial.meshlink.core.ui.icon.Info
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import org.jetbrains.compose.resources.stringResource

/** Informational progress that never captures Back or blocks navigation. */
@Composable
fun ChannelApplyStatus(state: ChannelApplyUiState, modifier: Modifier = Modifier) {
    val message =
        when (state) {
            ChannelApplyUiState.Applying ->
                stringResource(Res.string.channel_apply_in_progress, CHANNEL_APPLY_RESTART_SECONDS)

            ChannelApplyUiState.WaitingForReconnect ->
                stringResource(Res.string.channel_apply_pending, CHANNEL_APPLY_RESTART_SECONDS)

            ChannelApplyUiState.InvalidSettings -> stringResource(Res.string.channel_apply_invalid)

            else -> return
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == ChannelApplyUiState.Applying) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            } else {
                Icon(imageVector = MeshtasticIcons.Info, contentDescription = null)
            }
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
