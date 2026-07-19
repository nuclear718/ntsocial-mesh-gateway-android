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
package com.ntsocial.meshlink.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.ui.component.MeshtasticAppShell
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavDisplay
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavigationSuite
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.desktop.navigation.desktopNavGraph

/**
 * Desktop main screen — assembles the shared [MeshtasticAppShell], [MeshtasticNavigationSuite], and
 * [MeshtasticNavDisplay] with the desktop-specific [desktopNavGraph] entry provider.
 */
@Composable
fun DesktopMainScreen(uiViewModel: UIViewModel, multiBackstack: MultiBackstack) {
    val backStack = multiBackstack.activeBackStack

    Surface(modifier = Modifier.fillMaxSize()) {
        MeshtasticAppShell(
            multiBackstack = multiBackstack,
            uiViewModel = uiViewModel,
            hostModifier = Modifier.padding(bottom = 24.dp),
        ) {
            MeshtasticNavigationSuite(
                multiBackstack = multiBackstack,
                uiViewModel = uiViewModel,
                modifier = Modifier.fillMaxSize(),
            ) {
                val provider = entryProvider<NavKey> { desktopNavGraph(backStack, uiViewModel, multiBackstack) }
                MeshtasticNavDisplay(
                    multiBackstack = multiBackstack,
                    entryProvider = provider,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
