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
package com.ntsocial.meshlink.feature.connections.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSnapshot
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.connect
import com.ntsocial.meshlink.core.resources.connected
import com.ntsocial.meshlink.core.resources.connecting
import com.ntsocial.meshlink.core.resources.disconnect
import com.ntsocial.meshlink.core.resources.disconnected
import com.ntsocial.meshlink.core.resources.error
import com.ntsocial.meshlink.core.resources.loading
import com.ntsocial.meshlink.core.resources.radio_fleet_capacity
import com.ntsocial.meshlink.core.resources.radio_fleet_title
import com.ntsocial.meshlink.core.resources.radio_legacy_primary
import com.ntsocial.meshlink.core.resources.remove
import com.ntsocial.meshlink.core.resources.select
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
actual fun RadioFleetPanel(modifier: Modifier) {
    val fleetManager = koinInject<RadioFleetManager>()
    val snapshots by fleetManager.snapshots.collectAsStateWithLifecycle()
    val selectedEndpointId by fleetManager.selectedEndpointId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val endpoints =
        snapshots.values.sortedWith(
            compareByDescending<RadioEndpointSnapshot> { it.profile.legacyPrimary }.thenBy { it.profile.displayName },
        )

    if (endpoints.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(Res.string.radio_fleet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.radio_fleet_capacity, endpoints.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = endpoints, key = { it.profile.id.value }) { snapshot ->
                val selected = snapshot.profile.id == selectedEndpointId
                RadioEndpointCard(
                    snapshot = snapshot,
                    selected = selected,
                    onSelect = { scope.launch { fleetManager.select(snapshot.profile.id) } },
                    onConnectionToggle = {
                        scope.launch {
                            if (snapshot.state.isActive) {
                                fleetManager.disconnect(snapshot.profile.id)
                            } else {
                                fleetManager.connect(snapshot.profile.id)
                            }
                        }
                    },
                    onRemove = { scope.launch { fleetManager.remove(snapshot.profile.id) } },
                )
            }
        }
    }
}

@Composable
private fun RadioEndpointCard(
    snapshot: RadioEndpointSnapshot,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnectionToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
        border =
        BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EndpointCardDetails(snapshot)
            EndpointCardActions(
                snapshot = snapshot,
                selected = selected,
                onSelect = onSelect,
                onConnectionToggle = onConnectionToggle,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun EndpointCardDetails(snapshot: RadioEndpointSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = snapshot.profile.displayName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = snapshot.profile.addressSuffix.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (snapshot.profile.legacyPrimary) {
            Text(
                text = stringResource(Res.string.radio_legacy_primary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = endpointStateLabel(snapshot.state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EndpointCardActions(
    snapshot: RadioEndpointSnapshot,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnectionToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (!selected) {
            TextButton(onClick = onSelect) { Text(stringResource(Res.string.select)) }
        }
        TextButton(onClick = onConnectionToggle) {
            Text(stringResource(if (snapshot.state.isActive) Res.string.disconnect else Res.string.connect))
        }
        if (!snapshot.profile.legacyPrimary) {
            TextButton(onClick = onRemove) { Text(stringResource(Res.string.remove)) }
        }
    }
}

@Composable
private fun endpointStateLabel(state: EndpointSessionState): String = when (state) {
    is EndpointSessionState.Ready -> stringResource(Res.string.connected)

    EndpointSessionState.Connecting -> stringResource(Res.string.connecting)

    EndpointSessionState.Synchronizing,
    EndpointSessionState.WaitingResource,
    -> stringResource(Res.string.loading)

    is EndpointSessionState.Failed,
    is EndpointSessionState.Degraded,
    -> stringResource(Res.string.error)

    EndpointSessionState.Registered -> stringResource(Res.string.disconnected)
}

private val EndpointSessionState.isActive: Boolean
    get() =
        this is EndpointSessionState.Ready ||
            this is EndpointSessionState.Connecting ||
            this is EndpointSessionState.Synchronizing ||
            this is EndpointSessionState.WaitingResource
