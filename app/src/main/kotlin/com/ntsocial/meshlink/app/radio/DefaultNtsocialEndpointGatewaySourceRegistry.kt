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

import com.ntsocial.meshlink.core.service.MutableNtsocialEndpointGatewaySourceRegistry
import com.ntsocial.meshlink.core.service.NtsocialEndpointGatewaySource
import com.ntsocial.meshlink.core.service.NtsocialEndpointGatewaySourceRegistry
import com.ntsocial.meshlink.core.service.RegisteredNtsocialEndpointGatewaySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(binds = [NtsocialEndpointGatewaySourceRegistry::class, MutableNtsocialEndpointGatewaySourceRegistry::class])
class DefaultNtsocialEndpointGatewaySourceRegistry : MutableNtsocialEndpointGatewaySourceRegistry {
    private val scope = CoroutineScope(SupervisorJob())
    private val mutableEntries = MutableStateFlow<Map<String, RegisteredNtsocialEndpointGatewaySource>>(emptyMap())
    private val mutableRevision = MutableStateFlow(0L)
    private val revisionJobs = mutableMapOf<String, Job>()

    override val entries: StateFlow<Map<String, RegisteredNtsocialEndpointGatewaySource>> = mutableEntries.asStateFlow()
    override val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    override fun register(runtimeToken: String, source: NtsocialEndpointGatewaySource) {
        require(runtimeToken.isNotBlank())
        val endpointId = source.endpointId
        val previous = mutableEntries.value[endpointId]
        if (previous?.runtimeToken == runtimeToken && previous.source === source) return
        revisionJobs.remove(endpointId)?.cancel()
        mutableEntries.update { current ->
            current +
                (endpointId to RegisteredNtsocialEndpointGatewaySource(runtimeToken = runtimeToken, source = source))
        }
        revisionJobs[endpointId] = scope.launch { source.revision.collect { mutableRevision.update { it + 1L } } }
        mutableRevision.update { it + 1L }
    }

    override fun unregister(endpointId: String, runtimeToken: String) {
        val current = mutableEntries.value[endpointId]
        if (current?.runtimeToken != runtimeToken) return
        revisionJobs.remove(endpointId)?.cancel()
        mutableEntries.update { it - endpointId }
        mutableRevision.update { it + 1L }
    }
}
