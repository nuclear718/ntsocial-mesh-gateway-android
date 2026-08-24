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
package com.ntsocial.meshlink.app.radio

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.repository.MeshPrefs
import com.ntsocial.meshlink.core.repository.RadioPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FixedEndpointRadioPrefs(private val profile: RadioEndpointProfile) : RadioPrefs {
    override val devAddr: StateFlow<String?> = MutableStateFlow(profile.transportAddress)
    override val devName: StateFlow<String?> = MutableStateFlow(profile.displayName)

    override suspend fun readPersistedDevAddr(): String = profile.transportAddress

    override fun setDevAddr(address: String?) {
        require(address == null || address == devAddr.value) { "Endpoint sessions cannot change transport identity" }
    }

    override fun setDevName(name: String?) = Unit
}

internal class FixedEndpointMeshPrefs(profile: RadioEndpointProfile, private val delegate: MeshPrefs) : MeshPrefs {
    override val deviceAddress: StateFlow<String?> = MutableStateFlow(profile.transportAddress)

    override fun setDeviceAddress(address: String?) {
        require(address == null || address == deviceAddress.value) {
            "Endpoint sessions cannot change database identity"
        }
    }

    override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> =
        delegate.getStoreForwardLastRequest(deviceAddress.value)

    override fun setStoreForwardLastRequest(address: String?, timestamp: Int) {
        delegate.setStoreForwardLastRequest(deviceAddress.value, timestamp)
    }
}
