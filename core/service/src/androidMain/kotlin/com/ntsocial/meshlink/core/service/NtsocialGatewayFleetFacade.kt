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
package com.ntsocial.meshlink.core.service

import android.content.Context
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.radiofleet.MAX_RADIO_ENDPOINTS
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString
import org.koin.core.annotation.Single
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Sanitized, endpoint-scoped state consumed only by the protected Gateway v3 boundary. */
data class NtsocialGatewayEndpointSnapshot(
    val endpointId: String,
    val displayName: String,
    val addressSuffix: String?,
    val protocol: String,
    val sessionState: String,
    val endpointGeneration: String,
    val catalogGeneration: String,
    val historyEpoch: String,
    val messageChangeSeq: Long,
    val nativeHistoryAvailable: Boolean,
    val nativeTextSendAvailable: Boolean,
    val arbitraryRouteOverlayAvailable: Boolean,
    val hasCachedCatalog: Boolean,
    val appearanceToken: String?,
    val sortOrder: Int,
    val channels: List<NtsocialGatewayEndpointChannel>,
) {
    init {
        require(isValidGatewayEndpointId(endpointId)) { "Invalid Gateway endpoint ID" }
        require(endpointGeneration.isNotBlank()) { "Endpoint generation cannot be blank" }
        require(catalogGeneration.isNotBlank()) { "Catalog generation cannot be blank" }
        require(historyEpoch.isNotBlank()) { "History epoch cannot be blank" }
        require(messageChangeSeq >= 0L) { "Message change sequence cannot be negative" }
    }
}

data class NtsocialGatewayEndpointChannel(
    val sourceChannelId: String,
    val slotIndex: Int,
    val role: String,
    val configuredName: String,
    val displayName: String,
    val securityClass: String,
    val uplinkEnabled: Boolean,
    val downlinkEnabled: Boolean,
    val canReadNativeText: Boolean,
    val canSendNativeText: Boolean,
    val canSendNtOverlay: Boolean,
)

data class NtsocialGatewayEndpointMessageChange(
    val sourceMessageId: String,
    val sourceChannelId: String,
    val originClientMessageId: String?,
    val changeSeq: Long,
    val packetId: Long,
    val fromNodeId: String,
    val fromDisplayName: String,
    val text: String,
    val senderTimestampMillis: Long,
    val receivedAtMillis: Long,
    val direction: String,
    val status: String?,
    val snr: Float?,
    val rssi: Int?,
    val hopsAway: Int?,
    val viaMqtt: Boolean,
)

/** A live endpoint owner. Implementations remain in the Android app module beside endpoint Koin scopes. */
interface NtsocialEndpointGatewaySource {
    val endpointId: String
    val revision: StateFlow<Long>

    suspend fun snapshot(): NtsocialGatewayEndpointSnapshot

    suspend fun messageChanges(after: Long, limit: Int): List<NtsocialGatewayEndpointMessageChange>

    suspend fun sendOverlay(
        rawEnvelope: ByteString,
        sourceChannelId: String,
        channelIndex: Int,
        to: String?,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): Int

    suspend fun sendNativeText(
        text: String,
        sourceChannelId: String,
        channelIndex: Int,
        packetId: Int,
        originClientMessageId: String,
    ): Int
}

data class RegisteredNtsocialEndpointGatewaySource(val runtimeToken: String, val source: NtsocialEndpointGatewaySource)

interface NtsocialEndpointGatewaySourceRegistry {
    val entries: StateFlow<Map<String, RegisteredNtsocialEndpointGatewaySource>>
    val revision: StateFlow<Long>
}

interface MutableNtsocialEndpointGatewaySourceRegistry : NtsocialEndpointGatewaySourceRegistry {
    fun register(runtimeToken: String, source: NtsocialEndpointGatewaySource)

    fun unregister(endpointId: String, runtimeToken: String)
}

data class NtsocialGatewayFleetSnapshot(
    val providerInstanceId: String,
    val fleetGeneration: String,
    val endpoints: List<NtsocialGatewayEndpointSnapshot>,
) {
    val liveEndpointCount: Int
        get() = endpoints.count { it.sessionState == "READY" }
}

@Single
class NtsocialGatewayFleetFacade(context: Context, private val registry: NtsocialEndpointGatewaySourceRegistry) {
    private val providerInstanceId =
        context.getSharedPreferences(PROVIDER_PREFS, Context.MODE_PRIVATE).let { preferences ->
            preferences.getString(PROVIDER_INSTANCE_ID, null)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also { generated ->
                    check(preferences.edit().putString(PROVIDER_INSTANCE_ID, generated).commit()) {
                        "Unable to persist Gateway provider instance identity"
                    }
                }
        }

    suspend fun snapshot(): NtsocialGatewayFleetSnapshot {
        val endpoints =
            registry.entries.value.values
                .map { it.source.snapshot() }
                .sortedWith(compareBy<NtsocialGatewayEndpointSnapshot> { it.sortOrder }.thenBy { it.endpointId })
        return NtsocialGatewayFleetSnapshot(
            providerInstanceId = providerInstanceId,
            fleetGeneration = fleetGeneration(endpoints),
            endpoints = endpoints,
        )
    }

    fun source(endpointId: String): NtsocialEndpointGatewaySource? = registry.entries.value[endpointId]?.source

    companion object {
        const val MAX_ENDPOINTS: Int = MAX_RADIO_ENDPOINTS
        const val MAX_COMMAND_BYTES: Int = NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES

        private const val PROVIDER_PREFS = "ntsocial_gateway_v3_provider"
        private const val PROVIDER_INSTANCE_ID = "provider_instance_id"

        internal fun fleetGeneration(endpoints: List<NtsocialGatewayEndpointSnapshot>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            endpoints
                .sortedBy { it.endpointId }
                .forEach { endpoint ->
                    listOf(
                        endpoint.endpointId,
                        endpoint.endpointGeneration,
                        endpoint.catalogGeneration,
                        endpoint.historyEpoch,
                    )
                        .forEach { value ->
                            digest.update(value.toByteArray(StandardCharsets.UTF_8))
                            digest.update(0)
                        }
                }
            return digest.digest().take(GENERATION_BYTES).joinToString("") { byte ->
                "%02X".format(byte.toInt() and UNSIGNED_BYTE_MASK)
            }
        }

        private const val GENERATION_BYTES = 16
        private const val UNSIGNED_BYTE_MASK = 0xFF
    }
}

private val GATEWAY_ENDPOINT_ID_PATTERN = Regex("^[A-Za-z0-9._:-]{1,96}$")

internal fun isValidGatewayEndpointId(value: String): Boolean = GATEWAY_ENDPOINT_ID_PATTERN.matches(value)
