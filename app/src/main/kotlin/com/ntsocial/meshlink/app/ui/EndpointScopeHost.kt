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
package com.ntsocial.meshlink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.app.radio.RadioEndpointScopeRegistry
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_hub_endpoint_unavailable
import com.ntsocial.meshlink.core.resources.radio_endpoint_switching
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.scope.UnboundKoinScope
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinDelicateAPI::class, KoinExperimentalAPI::class)
@Composable
fun EndpointScopeHost(endpointId: RadioEndpointId, expectedGeneration: Long, content: @Composable () -> Unit) {
    val fleetManager = koinInject<RadioFleetManager>()
    val scopeRegistry = koinInject<RadioEndpointScopeRegistry>()
    val snapshots by fleetManager.snapshots.collectAsStateWithLifecycle()
    val selectedEndpointId by fleetManager.selectedEndpointId.collectAsStateWithLifecycle()
    val endpointScopes by scopeRegistry.scopes.collectAsStateWithLifecycle()
    val snapshot = snapshots[endpointId]

    val generationMatches = snapshot?.generation == expectedGeneration
    val movableContent = remember(content) { movableContentOf { content() } }

    LaunchedEffect(endpointId, expectedGeneration, snapshot) {
        if (snapshot != null && generationMatches && selectedEndpointId != endpointId) {
            fleetManager.select(endpointId)
        }
    }

    when {
        snapshot == null -> EndpointUnavailableState()

        !generationMatches -> EndpointUnavailableState()

        selectedEndpointId != endpointId -> EndpointLoadingState()

        snapshot.profile.legacyPrimary -> movableContent()

        endpointScopes[endpointId] != null ->
            UnboundKoinScope(scope = checkNotNull(endpointScopes[endpointId])) { movableContent() }

        else -> EndpointLoadingState()
    }
}

@Composable
private fun EndpointUnavailableState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.channel_hub_endpoint_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EndpointLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.radio_endpoint_switching), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
