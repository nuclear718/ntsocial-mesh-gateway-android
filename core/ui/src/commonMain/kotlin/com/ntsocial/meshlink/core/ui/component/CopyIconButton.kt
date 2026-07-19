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

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.copy
import com.ntsocial.meshlink.core.ui.icon.Copy
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.util.createClipEntry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun CopyIconButton(
    valueToCopy: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.copy),
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    IconButton(
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                val clipEntry = createClipEntry(valueToCopy)
                clipboardManager.setClipEntry(clipEntry)
            }
        },
    ) {
        Icon(imageVector = MeshtasticIcons.Copy, contentDescription = label)
    }
}
