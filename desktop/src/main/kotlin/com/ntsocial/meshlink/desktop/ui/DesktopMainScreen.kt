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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ntsocial.meshlink.core.navigation.MultiBackstack
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_butterfly
import com.ntsocial.meshlink.core.ui.component.MeshtasticAppShell
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavDisplay
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavigationSuite
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.desktop.navigation.desktopNavGraph
import org.jetbrains.compose.resources.painterResource

private const val DARK_BACKGROUND_ALPHA = 0.72F
private const val LIGHT_BACKGROUND_ALPHA = 0.22F
private val DarkBackgroundScrim = Color(0x470E1420)
private val LightBackgroundScrim = Color(0xC7FFFFFF)

/**
 * Desktop main screen — assembles the shared [MeshtasticAppShell], [MeshtasticNavigationSuite], and
 * [MeshtasticNavDisplay] with the desktop-specific [desktopNavGraph] entry provider.
 */
@Suppress("ViewModelForwarding")
@Composable
fun DesktopMainScreen(
    uiViewModel: UIViewModel,
    multiBackstack: MultiBackstack,
    modifier: Modifier = Modifier,
    useWindowsBranding: Boolean = false,
    darkTheme: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (useWindowsBranding) {
            Image(
                painter = painterResource(Res.drawable.img_ntsocial_background_butterfly),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                Modifier.fillMaxSize().alpha(if (darkTheme) DARK_BACKGROUND_ALPHA else LIGHT_BACKGROUND_ALPHA),
            )
            Box(
                modifier =
                Modifier.fillMaxSize().background(if (darkTheme) DarkBackgroundScrim else LightBackgroundScrim),
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (useWindowsBranding) Color.Transparent else MaterialTheme.colorScheme.surface,
        ) {
            DesktopNavigationContent(uiViewModel, multiBackstack, useWindowsBranding)
        }
    }
}

@Suppress("ViewModelForwarding")
@Composable
private fun DesktopNavigationContent(
    uiViewModel: UIViewModel,
    multiBackstack: MultiBackstack,
    useWindowsBranding: Boolean,
) {
    val backStack = multiBackstack.activeBackStack

    MeshtasticAppShell(
        multiBackstack = multiBackstack,
        uiViewModel = uiViewModel,
        hostModifier = Modifier.padding(bottom = 24.dp),
    ) {
        MeshtasticNavigationSuite(
            multiBackstack = multiBackstack,
            uiViewModel = uiViewModel,
            modifier = Modifier.fillMaxSize(),
            containerColor = if (useWindowsBranding) Color.Transparent else MaterialTheme.colorScheme.background,
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
