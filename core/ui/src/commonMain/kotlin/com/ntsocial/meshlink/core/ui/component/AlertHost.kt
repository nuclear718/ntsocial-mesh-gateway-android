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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.ui.util.AlertManager

/**
 * Shared composable that observes [AlertManager.currentAlert] and renders a [MeshtasticDialog] when an alert is
 * present. This eliminates duplicated alert-rendering boilerplate across Android and Desktop host shells.
 *
 * Usage: Place `AlertHost(alertManager)` once in the top-level composable of each platform host.
 */
@Composable
fun AlertHost(alertManager: AlertManager) {
    val alertDialogState by alertManager.currentAlert.collectAsStateWithLifecycle()
    alertDialogState?.let { state ->
        MeshtasticDialog(
            title = state.title,
            titleRes = state.titleRes,
            message = state.message,
            messageRes = state.messageRes,
            html = state.html,
            icon = state.icon,
            text = state.composableMessage?.let { msg -> { msg.Content() } },
            confirmText = state.confirmText,
            confirmTextRes = state.confirmTextRes,
            onConfirm = state.onConfirm,
            dismissText = state.dismissText,
            dismissTextRes = state.dismissTextRes,
            onDismiss = state.onDismiss,
            choices = state.choices,
            dismissable = state.dismissable,
        )
    }
}
