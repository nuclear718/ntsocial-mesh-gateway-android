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
package com.ntsocial.meshlink.core.gateway.apple

import com.ntsocial.meshlink.core.model.util.platformRandomBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.ByteString.Companion.toByteString

fun interface AppleGatewayRandomSource {
    fun nextBytes(size: Int): ByteArray
}

object PlatformAppleGatewayRandomSource : AppleGatewayRandomSource {
    override fun nextBytes(size: Int): ByteArray = platformRandomBytes(size)
}

data class AppleGatewayIssuedRoutes(val radioGeneration: String, val routes: List<AppleGatewayRoute>)

/**
 * Authoritative route state for one provider process.
 *
 * Tokens and their caller/slot bindings never leave this registry as authoritative state. The App Group contains only a
 * short-lived projection so the parent can present and return a capability. Constructing a new registry represents a
 * process start and therefore begins in a fresh opaque generation.
 */
class AppleGatewayRouteRegistry(private val randomSource: AppleGatewayRandomSource = PlatformAppleGatewayRandomSource) {
    private val mutex = Mutex()
    private val routesByToken = mutableMapOf<String, AppleGatewayRoute>()
    private var channelSnapshot: List<AppleGatewayRadioChannelIdentity> = emptyList()
    private var routingContext: ByteString = ByteString.EMPTY
    private var generation: String = newOpaqueValue()

    suspend fun currentGeneration(): String = mutex.withLock { generation }

    /** Issues a new route for every current slot while retaining older unexpired routes in the same generation. */
    suspend fun issueRoutes(
        callerId: String,
        channels: List<AppleGatewayRadioChannelIdentity>,
        nowMillis: Long,
        newRoutingContext: ByteString = ByteString.EMPTY,
    ): AppleGatewayIssuedRoutes = mutex.withLock {
        require(callerId.isNotBlank())
        require(channels.all { it.slotIndex >= 0 })
        require(channels.map { it.slotIndex }.distinct().size == channels.size) { "Channel slots must be unique" }

        val immutableSnapshot = channels.toList()
        if (immutableSnapshot != channelSnapshot || newRoutingContext != routingContext) {
            rotateGeneration()
            channelSnapshot = immutableSnapshot
            routingContext = newRoutingContext
        }
        routesByToken.entries.removeAll { (_, route) -> route.expiresAtMillis <= nowMillis }

        val routes =
            immutableSnapshot.map { channel ->
                val token = newUniqueRouteToken()
                AppleGatewayRoute(
                    callerId = callerId,
                    sourceChannelId = channel.sourceChannelId,
                    capturedSlotIndex = channel.slotIndex,
                    capabilities = channel.capabilities,
                    token = token,
                    radioGeneration = generation,
                    expiresAtMillis = nowMillis + AppleGatewayContract.ROUTE_TTL_MILLIS,
                )
                    .also { route -> routesByToken[token] = route }
            }
        AppleGatewayIssuedRoutes(generation, routes)
    }

    suspend fun resolve(command: AppleGatewayCommand, nowMillis: Long): AppleGatewayRoute? = mutex.withLock {
        routesByToken.entries.removeAll { (_, route) -> route.expiresAtMillis <= nowMillis }
        routesByToken[command.routeToken]?.takeIf { route ->
            route.callerId == command.callerId &&
                route.sourceChannelId == command.sourceChannelId &&
                route.radioGeneration == command.radioGeneration &&
                route.expiresAtMillis > nowMillis &&
                route.capturedSlotIndex >= 0 &&
                command.body.requiredRouteCapability() in route.capabilities
        }
    }

    suspend fun revokeCaller(callerId: String) =
        mutex.withLock { routesByToken.entries.removeAll { (_, route) -> route.callerId == callerId } }

    private fun rotateGeneration() {
        val previous = generation
        generation = newOpaqueValue(excluding = previous)
        routesByToken.clear()
    }

    private fun newUniqueRouteToken(): String {
        repeat(MAX_RANDOM_ATTEMPTS) {
            val candidate = newOpaqueValue()
            if (candidate !in routesByToken) return candidate
        }
        error("Cryptographic random source repeatedly produced a duplicate Apple Gateway route token")
    }

    private fun newOpaqueValue(excluding: String? = null): String {
        repeat(MAX_RANDOM_ATTEMPTS) {
            val bytes = randomSource.nextBytes(AppleGatewayContract.ROUTE_TOKEN_SIZE_BYTES)
            check(bytes.size == AppleGatewayContract.ROUTE_TOKEN_SIZE_BYTES) {
                "Apple Gateway random source returned ${bytes.size} bytes"
            }
            val candidate = bytes.toByteString().base64Url().trimEnd('=')
            if (candidate != excluding) return candidate
        }
        error("Cryptographic random source repeatedly produced the same Apple Gateway opaque value")
    }

    private companion object {
        const val MAX_RANDOM_ATTEMPTS = 8
    }
}
