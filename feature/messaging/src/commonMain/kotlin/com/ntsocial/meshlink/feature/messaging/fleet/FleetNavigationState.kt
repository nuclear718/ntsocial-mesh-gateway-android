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
package com.ntsocial.meshlink.feature.messaging.fleet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelsRepository
import com.ntsocial.meshlink.core.ui.component.NavigationRadioState
import org.koin.compose.koinInject

/** The connection badge and unread count describe the active fleet, including secondary radios. */
@Composable
fun rememberFleetNavigationState(): NavigationRadioState {
    val fleet = koinInject<RadioFleetManager>()
    val channels = koinInject<FleetChannelsRepository>()
    val snapshots by fleet.snapshots.collectAsStateWithLifecycle()
    val selected by fleet.selectedEndpointId.collectAsStateWithLifecycle()
    val groups by channels.groups.collectAsStateWithLifecycle()
    val active = snapshots.values.filter { it.profile.enabled }
    val connection =
        when {
            active.any { it.state is EndpointSessionState.Ready } -> ConnectionState.Connected

            active.any {
                it.state == EndpointSessionState.Connecting || it.state == EndpointSessionState.Synchronizing
            } -> ConnectionState.Connecting

            else -> ConnectionState.Disconnected
        }
    return NavigationRadioState(
        connectionState = connection,
        unreadCount = groups.filter { it.profile.enabled }.sumOf { it.totalUnreadCount },
        deviceAddress = active.firstOrNull { it.profile.id == selected }?.profile?.transportAddress,
    )
}
