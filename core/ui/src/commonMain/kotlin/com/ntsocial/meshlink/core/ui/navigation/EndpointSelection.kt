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
package com.ntsocial.meshlink.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ntsocial.meshlink.core.navigation.ChannelsRoute
import com.ntsocial.meshlink.core.navigation.EndpointTarget
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.navigation.withoutEndpoint
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.radio_endpoint_edits_reset
import com.ntsocial.meshlink.core.ui.util.SnackbarManager
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun BindEndpointSelection(backstack: MultiBackstack, target: EndpointTarget?) {
    val snackbar = koinInject<SnackbarManager>()
    val resetNotice = stringResource(Res.string.radio_endpoint_edits_reset)
    LaunchedEffect(target) {
        val discardsEditor =
            backstack.backStacks.values.any { stack ->
                stack.drop(1).any { it.withoutEndpoint() is SettingsRoute || it.withoutEndpoint() is ChannelsRoute }
            }
        if (backstack.endpointTarget != null && backstack.endpointTarget != target && discardsEditor) {
            snackbar.showSnackbar(resetNotice)
        }
        backstack.bindEndpoint(target)
    }
}
