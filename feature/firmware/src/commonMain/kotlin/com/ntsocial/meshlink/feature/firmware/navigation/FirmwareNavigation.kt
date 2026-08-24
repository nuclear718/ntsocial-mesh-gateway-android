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
package com.ntsocial.meshlink.feature.firmware.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.FirmwareRoute
import com.ntsocial.meshlink.core.ui.viewmodel.scopedViewModel
import com.ntsocial.meshlink.feature.firmware.FirmwareUpdateScreen
import com.ntsocial.meshlink.feature.firmware.FirmwareUpdateViewModel

/** Registers the firmware update screen entries into the Navigation 3 entry provider. */
fun EntryProviderScope<NavKey>.firmwareGraph(backStack: NavBackStack<NavKey>) {
    entry<FirmwareRoute.FirmwareGraph> {
        FirmwareScreen(onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }
    entry<FirmwareRoute.FirmwareUpdate> {
        FirmwareScreen(onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }
}

@Composable
private fun FirmwareScreen(onNavigateUp: () -> Unit) {
    val viewModel = scopedViewModel<FirmwareUpdateViewModel>()
    FirmwareUpdateScreen(onNavigateUp = onNavigateUp, viewModel = viewModel)
}
