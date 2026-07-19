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
package com.ntsocial.meshlink.feature.settings.radio.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.send
import com.ntsocial.meshlink.core.ui.component.MeshtasticDialog
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun WarningDialog(
    icon: ImageVector? = null,
    title: String,
    text: @Composable () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val resolvedIcon = icon ?: MeshtasticIcons.Warning

    MeshtasticDialog(
        onDismiss = onDismiss,
        icon = resolvedIcon,
        title = title,
        text = text,
        confirmText = stringResource(Res.string.send),
        onConfirm = {
            onDismiss()
            onConfirm()
        },
        dismissText = stringResource(Res.string.cancel),
    )
}
