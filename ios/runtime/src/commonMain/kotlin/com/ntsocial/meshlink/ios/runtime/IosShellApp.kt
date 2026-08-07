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
package com.ntsocial.meshlink.ios.runtime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_butterfly
import com.ntsocial.meshlink.core.resources.img_ntsocial_butterfly_logo
import com.ntsocial.meshlink.core.ui.component.MeshtasticNavDisplay
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import com.ntsocial.meshlink.ios.runtime.resources.ios_connection_title
import com.ntsocial.meshlink.ios.runtime.resources.ios_host_active
import com.ntsocial.meshlink.ios.runtime.resources.ios_host_inactive
import com.ntsocial.meshlink.ios.runtime.resources.ios_integration_title
import com.ntsocial.meshlink.ios.runtime.resources.ios_logo_description
import com.ntsocial.meshlink.ios.runtime.resources.ios_shell_app_name
import com.ntsocial.meshlink.ios.runtime.resources.ios_shell_subtitle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.ntsocial.meshlink.core.resources.Res as CoreRes
import com.ntsocial.meshlink.ios.runtime.resources.Res as IosRes

@Serializable
private sealed interface IosShellRoute : NavKey {
    @Serializable data object Connection : IosShellRoute

    @Serializable data object Integration : IosShellRoute
}

@OptIn(ExperimentalSerializationApi::class)
private val IosShellSavedStateConfig = SavedStateConfiguration {
    serializersModule = SerializersModule { polymorphic(NavKey::class) { subclassesOfSealed<IosShellRoute>() } }
}

@Composable
internal fun IosShellApp(controller: IosShellController) {
    val state by controller.state.collectAsState()
    val backStack = rememberNavBackStack(IosShellSavedStateConfig, IosShellRoute.Connection)

    LaunchedEffect(Unit) { controller.refreshHostReadiness() }

    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Image(
                painter = painterResource(CoreRes.drawable.img_ntsocial_background_butterfly),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(BACKGROUND_ALPHA),
            )
            IosNavigationShell(backStack = backStack, state = state, controller = controller)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosNavigationShell(backStack: NavBackStack<NavKey>, state: IosShellState, controller: IosShellController) {
    val activeRoute = backStack.lastOrNull()
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = Color.Transparent,
        topBar = { IosTopAppBar(state.hostActive) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SURFACE_ALPHA)) {
                ShellNavigationItem(
                    selected = activeRoute == IosShellRoute.Connection,
                    label = stringResource(IosRes.string.ios_connection_title),
                    onClick = { backStack.replaceWith(IosShellRoute.Connection) },
                )
                ShellNavigationItem(
                    selected = activeRoute == IosShellRoute.Integration,
                    label = stringResource(IosRes.string.ios_integration_title),
                    onClick = { backStack.replaceWith(IosShellRoute.Integration) },
                )
            }
        },
    ) { innerPadding ->
        val provider =
            entryProvider<NavKey> {
                entry<IosShellRoute.Connection> {
                    ConnectionScreen(
                        state = state.radio,
                        onRefreshBluetooth = controller::refreshBluetooth,
                        onToggleScan = controller::toggleRadioScan,
                        onConnect = controller::connectRadio,
                        onDisconnect = controller::disconnectRadio,
                        onForget = controller::forgetRadio,
                    )
                }
                entry<IosShellRoute.Integration> {
                    IntegrationStatusScreen(state = state, onRefresh = controller::refreshHostReadiness)
                }
            }
        MeshtasticNavDisplay(
            backStack = backStack,
            entryProvider = provider,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosTopAppBar(hostActive: Boolean) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(CoreRes.drawable.img_ntsocial_butterfly_logo),
                    contentDescription = stringResource(IosRes.string.ios_logo_description),
                    modifier = Modifier.size(40.dp),
                )
                Column {
                    Text(
                        text = stringResource(IosRes.string.ios_shell_app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(IosRes.string.ios_shell_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            val label =
                stringResource(if (hostActive) IosRes.string.ios_host_active else IosRes.string.ios_host_inactive)
            StatusPill(label = label, positive = hostActive, modifier = Modifier.padding(end = 12.dp))
        },
    )
}

@Composable
private fun RowScope.ShellNavigationItem(selected: Boolean, label: String, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Surface(
                modifier = Modifier.size(if (selected) 10.dp else 8.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                content = {},
            )
        },
        label = { Text(label) },
    )
}

private fun NavBackStack<NavKey>.replaceWith(route: NavKey) {
    if (lastOrNull() != route) {
        clear()
        add(route)
    }
}

private const val BACKGROUND_ALPHA = 0.12F
internal const val SURFACE_ALPHA = 0.94F
