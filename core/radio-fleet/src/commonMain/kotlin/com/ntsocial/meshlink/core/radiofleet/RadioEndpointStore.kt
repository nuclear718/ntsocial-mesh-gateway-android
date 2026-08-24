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
package com.ntsocial.meshlink.core.radiofleet

import kotlinx.coroutines.flow.StateFlow

interface RadioEndpointStore {
    val profiles: StateFlow<List<RadioEndpointProfile>>

    val selectedEndpointId: StateFlow<RadioEndpointId?>

    suspend fun migrateLegacySelection(address: String?, name: String?): RadioEndpointProfile?

    suspend fun register(candidate: DiscoveredRadio): RadioEndpointProfile

    suspend fun update(profile: RadioEndpointProfile)

    suspend fun select(endpointId: RadioEndpointId)

    suspend fun setLegacyPrimary(endpointId: RadioEndpointId)

    suspend fun remove(endpointId: RadioEndpointId)
}
