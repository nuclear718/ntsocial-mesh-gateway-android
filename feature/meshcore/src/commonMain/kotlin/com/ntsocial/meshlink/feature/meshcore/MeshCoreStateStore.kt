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
package com.ntsocial.meshlink.feature.meshcore

import com.ntsocial.meshlink.core.meshcore.MeshCoreChannel
import com.ntsocial.meshlink.core.meshcore.MeshCoreConnectionState
import com.ntsocial.meshlink.core.meshcore.MeshCoreContact
import com.ntsocial.meshlink.core.meshcore.MeshCoreDeviceInfo
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessage
import com.ntsocial.meshlink.core.meshcore.MeshCoreSelfInfo
import com.ntsocial.meshlink.core.meshcore.MeshCoreTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

/**
 * Feature-owned MeshCore state boundary.
 *
 * The later transport implementation can feed synchronized snapshots into this store without coupling the UI to
 * Meshtastic repositories or service state.
 */
@Single
class MeshCoreStateStore {
    private val mutableState = MutableStateFlow(MeshCoreUiState())
    val state: StateFlow<MeshCoreUiState> = mutableState.asStateFlow()

    fun selectSection(section: MeshCoreSection) {
        mutableState.update { it.copy(selectedSection = section) }
    }

    @Suppress("LongParameterList")
    fun replaceSnapshot(
        connectionState: MeshCoreConnectionState,
        activeTransport: MeshCoreTransport?,
        transportAvailable: Boolean,
        selfInfo: MeshCoreSelfInfo?,
        deviceInfo: MeshCoreDeviceInfo?,
        contacts: List<MeshCoreContact>,
        channels: List<MeshCoreChannel>,
        messages: List<MeshCoreMessage>,
    ) {
        mutableState.update { current ->
            current.copy(
                connectionState = connectionState,
                activeTransport = activeTransport,
                transportAvailable = transportAvailable,
                selfInfo = selfInfo,
                deviceInfo = deviceInfo,
                contacts = contacts,
                channels = channels,
                messages = messages,
            )
        }
    }
}
