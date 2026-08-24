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

import com.ntsocial.meshlink.core.common.util.normalizeAddress
import kotlin.jvm.JvmInline

const val MAX_RADIO_ENDPOINTS: Int = 4

@JvmInline
value class RadioEndpointId(val value: String) {
    init {
        require(value.isNotBlank()) { "Radio endpoint ID cannot be blank" }
    }

    override fun toString(): String = value
}

enum class RadioProtocol {
    MESHTASTIC,
}

data class RadioEndpointProfile(
    val id: RadioEndpointId,
    val protocol: RadioProtocol,
    val transportAddress: String,
    val displayName: String,
    val enabled: Boolean = true,
    val autoConnect: Boolean = true,
    val priority: Int = DEFAULT_RADIO_PRIORITY,
    val legacyPrimary: Boolean = false,
) {
    init {
        require(transportAddress.isNotBlank()) { "Radio transport address cannot be blank" }
    }

    val normalizedAddress: String
        get() = normalizeAddress(transportAddress)

    val addressSuffix: String
        get() = normalizedAddress.takeLast(ADDRESS_SUFFIX_LENGTH)
}

data class DiscoveredRadio(
    val protocol: RadioProtocol = RadioProtocol.MESHTASTIC,
    val transportAddress: String,
    val displayName: String,
)

sealed interface EndpointSessionState {
    data object Registered : EndpointSessionState

    data object Connecting : EndpointSessionState

    data object Synchronizing : EndpointSessionState

    data class Ready(val generation: Long) : EndpointSessionState

    data class Degraded(val reason: String) : EndpointSessionState

    data object WaitingResource : EndpointSessionState

    data class Failed(val retryAtMillis: Long? = null, val reason: String? = null) : EndpointSessionState
}

data class RadioEndpointSnapshot(
    val profile: RadioEndpointProfile,
    val state: EndpointSessionState = EndpointSessionState.Registered,
    val generation: Long = 0L,
    val primaryChannelName: String? = null,
    val lastReceivedAtMillis: Long? = null,
)

class RadioEndpointLimitExceededException(maxEndpoints: Int = MAX_RADIO_ENDPOINTS) :
    IllegalStateException("At most $maxEndpoints radio endpoints can be registered")

class StaleRadioEndpointGenerationException(
    endpointId: RadioEndpointId,
    expectedGeneration: Long,
    actualGeneration: Long,
) : IllegalStateException("Endpoint $endpointId generation changed from $expectedGeneration to $actualGeneration")

const val DEFAULT_RADIO_PRIORITY: Int = 100
private const val ADDRESS_SUFFIX_LENGTH = 4
