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

import com.ntsocial.meshlink.core.database.EndpointDatabaseHandle
import com.ntsocial.meshlink.core.datastore.RadioScopedDataSources
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.repository.MeshPrefs
import com.ntsocial.meshlink.core.repository.RadioPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.scope.Scope

internal data class IosRadioEndpointScopeContext(
    val profile: RadioEndpointProfile,
    val database: EndpointDatabaseHandle,
    val dataSources: RadioScopedDataSources,
    val serviceScope: CoroutineScope,
    val radioPrefs: RadioPrefs,
    val meshPrefs: MeshPrefs,
)

/** Exact runtime-token registry used by Compose and the fleet projection to find a live endpoint graph. */
internal class IosRadioEndpointScopeRegistry {
    private val mutableScopes = MutableStateFlow<Map<RadioEndpointId, Scope>>(emptyMap())
    val scopes: StateFlow<Map<RadioEndpointId, Scope>> = mutableScopes

    fun register(endpointId: RadioEndpointId, scope: Scope) {
        mutableScopes.value += endpointId to scope
    }

    fun unregister(endpointId: RadioEndpointId, expectedScope: Scope? = null) {
        val current = mutableScopes.value[endpointId]
        if (current != null && (expectedScope == null || current === expectedScope)) {
            mutableScopes.value -= endpointId
        }
    }
}
