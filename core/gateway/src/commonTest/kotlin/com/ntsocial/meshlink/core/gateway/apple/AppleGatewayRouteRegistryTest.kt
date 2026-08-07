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

import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleGatewayRouteRegistryTest {
    @Test
    fun `routes are random caller source slot generation bound and expire`() = runTest {
        val random = DeterministicAppleGatewayRandomSource()
        val registry = AppleGatewayRouteRegistry(random)
        val processGeneration = registry.currentGeneration()
        val issued = registry.issueRoutes(CALLER, listOf(channel()), NOW)
        val route = issued.routes.single()

        assertNotEquals(processGeneration, issued.radioGeneration)
        assertEquals(43, route.token.length)
        assertTrue(route.token.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        assertEquals(AppleGatewayContract.ROUTE_TTL_MILLIS, route.expiresAtMillis - NOW)
        assertNotNull(registry.resolve(command(route), NOW))
        assertNull(registry.resolve(command(route).copy(callerId = "com.ntsocial.other"), NOW))
        assertNull(registry.resolve(command(route).copy(sourceChannelId = OTHER_SOURCE_CHANNEL), NOW))
        assertNull(registry.resolve(command(route).copy(radioGeneration = "stale-generation"), NOW))
        assertNull(registry.resolve(command(route), route.expiresAtMillis))
    }

    @Test
    fun `same snapshot retains generation while any inequality rotates and revokes old routes`() = runTest {
        val registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource())
        val first = registry.issueRoutes(CALLER, listOf(channel()), NOW)
        val second = registry.issueRoutes(CALLER, listOf(channel()), NOW + 1)

        assertEquals(first.radioGeneration, second.radioGeneration)
        assertNotEquals(first.routes.single().token, second.routes.single().token)
        assertNotNull(registry.resolve(command(first.routes.single()), NOW + 1))

        val changed = registry.issueRoutes(CALLER, listOf(channel().copy(displayName = "Renamed")), NOW + 2)
        assertNotEquals(first.radioGeneration, changed.radioGeneration)
        assertNull(registry.resolve(command(first.routes.single()), NOW + 2))
        assertNotNull(registry.resolve(command(changed.routes.single()), NOW + 2))
    }

    @Test
    fun `private routing context inequality rotates generation without becoming generation`() = runTest {
        val registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource())
        val first = registry.issueRoutes(CALLER, listOf(channel()), NOW, "config-a".encodeUtf8())
        val second = registry.issueRoutes(CALLER, listOf(channel()), NOW + 1, "config-b".encodeUtf8())

        assertNotEquals(first.radioGeneration, second.radioGeneration)
        assertNotEquals("config-b", second.radioGeneration)
        assertNull(registry.resolve(command(first.routes.single()), NOW + 1))
        assertNotNull(registry.resolve(command(second.routes.single()), NOW + 1))
    }

    @Test
    fun `route capability rejects a command type that was not projected`() = runTest {
        val registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource())
        val issued =
            registry.issueRoutes(
                CALLER,
                listOf(channel().copy(capabilities = setOf(AppleGatewayRouteCapability.SEND_NTSOCIAL_ENVELOPE))),
                NOW,
            )

        assertNull(registry.resolve(command(issued.routes.single()), NOW))
    }

    @Test
    fun `new process registry cannot resolve previous process projection`() = runTest {
        val random = DeterministicAppleGatewayRandomSource()
        val firstRegistry = AppleGatewayRouteRegistry(random)
        val oldRoute = firstRegistry.issueRoutes(CALLER, listOf(channel()), NOW).routes.single()
        val restartedRegistry = AppleGatewayRouteRegistry(random)

        assertNotEquals(oldRoute.radioGeneration, restartedRegistry.currentGeneration())
        assertNull(restartedRegistry.resolve(command(oldRoute), NOW + 1))
    }

    private fun channel() = AppleGatewayRadioChannelIdentity(
        sourceChannelId = SOURCE_CHANNEL,
        slotIndex = 3,
        displayName = "Mesh",
        role = "SECONDARY",
        securityClass = "CUSTOM",
        capabilities = AppleGatewayRouteCapability.entries.toSet(),
    )

    private fun command(route: AppleGatewayRoute) = AppleGatewayCommand(
        callerId = route.callerId,
        requestId = "request",
        clientMessageId = CLIENT_ID,
        sourceChannelId = route.sourceChannelId,
        routeToken = route.token,
        radioGeneration = route.radioGeneration,
        issuedAtMillis = NOW,
        expiresAtMillis = NOW + AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS,
        keyVersion = 1,
        nonce = okio.ByteString.of(*ByteArray(AppleGatewayContract.COMMAND_NONCE_SIZE_BYTES)),
        body = AppleGatewayCommandBody.NativeBroadcastText("hello"),
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val CALLER = AppleGatewayContract.PARENT_CALLER_ID
        const val CLIENT_ID = "00112233445566778899AABBCCDDEEFF"
        const val SOURCE_CHANNEL = "meshtastic:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val OTHER_SOURCE_CHANNEL = "meshtastic:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    }
}

internal class DeterministicAppleGatewayRandomSource(initialCounter: Int = 0) : AppleGatewayRandomSource {
    private var counter = initialCounter

    override fun nextBytes(size: Int): ByteArray =
        ByteArray(size) { index -> (counter + index).toByte() }.also { counter += 1 }
}
