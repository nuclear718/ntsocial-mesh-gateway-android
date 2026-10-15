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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.EndpointRoute
import com.ntsocial.meshlink.core.navigation.EndpointTarget
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.navigation.isRadioDestination
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_hub_endpoint_unavailable
import com.ntsocial.meshlink.core.resources.connections
import com.ntsocial.meshlink.core.resources.nodes_empty_disconnected_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Insets belong above both the selector and page; consuming them here avoids padding child app bars again. */
@Composable
fun Modifier.endpointTabsPadding(enabled: Boolean): Modifier = if (enabled) {
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
} else {
    this
}

/** Captures the entry's owner now, rather than consulting selection when a delayed callback runs. */
@Composable
fun rememberEndpointNavigator(backStack: NavBackStack<NavKey>, replace: Boolean = false): (NavKey) -> Unit {
    val target = currentKoinScope().getOrNull<EndpointTarget>()
    return remember(backStack, target, replace) {
        { destination ->
            val entry = target?.let { EndpointRoute.Destination(it, destination) } ?: destination
            if (replace && backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
            backStack.add(entry)
        }
    }
}

fun endpointEntryProvider(
    provider: (NavKey) -> NavEntry<NavKey>,
    onNavigateToConnections: () -> Unit,
    endpointContent: @Composable (EndpointTarget, @Composable () -> Unit) -> Unit,
): (NavKey) -> NavEntry<NavKey> = { key ->
    if (key is EndpointRoute.Destination) {
        val entry = provider(key.destination)
        NavEntry(key = key, contentKey = key.toString(), metadata = entry.metadata) {
            endpointContent(key.target) { entry.Content() }
        }
    } else if (key.isRadioDestination() && !key.hasExplicitOrGlobalContext()) {
        NavEntry(key = key) { EndpointUnavailableContent(onNavigateToConnections) }
    } else {
        provider(key)
    }
}

@Composable
private fun EndpointUnavailableContent(onNavigateToConnections: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.nodes_empty_disconnected_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.channel_hub_endpoint_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onNavigateToConnections) { Text(stringResource(Res.string.connections)) }
    }
}

private fun NavKey.hasExplicitOrGlobalContext(): Boolean = when (this) {
    is ContactsRoute.FleetMessages,
    is ContactsRoute.EndpointContacts,
    is ContactsRoute.FleetShare,
    is ContactsRoute.FleetQuickChat,
    is SettingsRoute.SettingsGraph,
    SettingsRoute.About,
    -> true

    else -> false
}
