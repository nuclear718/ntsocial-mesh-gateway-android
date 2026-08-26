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
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NtsocialGatewayRouteTokenStoreTest {
    private val caller = NtsocialGatewayCaller(uid = 42, packageName = "com.ntsocial.android")
    private lateinit var context: Context
    private lateinit var store: NtsocialGatewayRouteTokenStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearLedger()
        store = NtsocialGatewayRouteTokenStore(context)
    }

    @After
    fun tearDown() {
        clearLedger()
    }

    @Test
    fun `route is caller source and generation bound`() {
        val token =
            store.issue(
                caller = caller,
                sourceChannelId = "meshtastic:alpha",
                channelIndex = 3,
                radioGeneration = "generation-a",
                nowMillis = 1_000L,
            )

        assertEquals(
            3,
            store.resolve(token, caller, "meshtastic:alpha", "generation-a", nowMillis = 1_001L)?.channelIndex,
        )
        assertNull(store.resolve(token, caller.copy(uid = 43), "meshtastic:alpha", "generation-a", 1_001L))
        assertNull(store.resolve(token, caller, "meshtastic:beta", "generation-a", 1_001L))
        assertNull(store.resolve(token, caller, "meshtastic:alpha", "generation-b", 1_001L))
        assertNull(store.resolve(token, caller, "meshtastic:alpha", "generation-a", 121_000L))
    }

    @Test
    fun `v3 route is endpoint generation and fleet bound`() {
        val token =
            store.issueV3(
                caller = caller,
                endpointId = "endpoint-a",
                sourceChannelId = "meshtastic:alpha",
                channelIndex = 0,
                endpointGeneration = "endpoint-generation-a",
                fleetGeneration = "fleet-a",
                nowMillis = 1_000L,
            )

        assertEquals(
            0,
            store
                .resolveV3(
                    token = token,
                    caller = caller,
                    endpointId = "endpoint-a",
                    sourceChannelId = "meshtastic:alpha",
                    endpointGeneration = "endpoint-generation-a",
                    fleetGeneration = "fleet-a",
                    nowMillis = 1_001L,
                )
                ?.channelIndex,
        )
        assertNull(
            store.resolveV3(
                token = token,
                caller = caller,
                endpointId = "endpoint-b",
                sourceChannelId = "meshtastic:alpha",
                endpointGeneration = "endpoint-generation-a",
                fleetGeneration = "fleet-a",
                nowMillis = 1_001L,
            ),
        )
        assertNull(
            store.resolveV3(
                token = token,
                caller = caller,
                endpointId = "endpoint-a",
                sourceChannelId = "meshtastic:alpha",
                endpointGeneration = "endpoint-generation-b",
                fleetGeneration = "fleet-a",
                nowMillis = 1_001L,
            ),
        )
    }

    @Test
    fun `accepted client message survives store recreation with deterministic packet id`() {
        val clientMessageId = "0123456789ABCDEF0123456789ABCDEF"
        val fingerprint = "A".repeat(64)
        val pending =
            assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
                store.reserveClientMessage(caller, clientMessageId, fingerprint),
            )
        assertEquals(true, store.markClientMessageAccepted(caller, clientMessageId, fingerprint, pending.packetId))

        val recreated = NtsocialGatewayRouteTokenStore(context)
        val accepted =
            assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Accepted>(
                recreated.reserveClientMessage(caller.copy(uid = 99), clientMessageId, fingerprint),
            )
        assertEquals(pending.packetId, accepted.packetId)
        assertEquals(
            NtsocialGatewayRouteTokenStore.ClientMessageReservation.Conflict,
            recreated.reserveClientMessage(caller, clientMessageId, "B".repeat(64)),
        )
    }

    @Test
    fun `pending client message survives crash window with same packet id`() {
        val clientMessageId = "ABCDEF0123456789ABCDEF0123456789"
        val fingerprint = "C".repeat(64)
        val first =
            assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
                store.reserveClientMessage(caller, clientMessageId, fingerprint),
            )

        val recreated = NtsocialGatewayRouteTokenStore(context)
        val retry =
            assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
                recreated.reserveClientMessage(caller, clientMessageId, fingerprint),
            )

        assertEquals(first.packetId, retry.packetId)
    }

    @Test
    fun `failed reservation commit rolls memory back`() {
        val clientMessageId = "11111111111111111111111111111111"
        store.persistClientMessagesOverride = { false }

        assertFailsWith<IllegalStateException> { store.reserveClientMessage(caller, clientMessageId, "A".repeat(64)) }

        store.persistClientMessagesOverride = null
        assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
            store.reserveClientMessage(caller, clientMessageId, "B".repeat(64)),
        )
    }

    @Test
    fun `failed accepted commit remains pending in memory and on restart`() {
        val clientMessageId = "22222222222222222222222222222222"
        val fingerprint = "C".repeat(64)
        val pending =
            assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
                store.reserveClientMessage(caller, clientMessageId, fingerprint),
            )
        store.persistClientMessagesOverride = { false }

        assertEquals(false, store.markClientMessageAccepted(caller, clientMessageId, fingerprint, pending.packetId))
        assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
            store.reserveClientMessage(caller, clientMessageId, fingerprint),
        )
        assertIs<NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending>(
            NtsocialGatewayRouteTokenStore(context).reserveClientMessage(caller, clientMessageId, fingerprint),
        )
    }

    private fun clearLedger() {
        if (::context.isInitialized) {
            context.getSharedPreferences(LEDGER_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val LEDGER_PREFERENCES = "ntsocial_gateway_v2_client_messages"
    }
}
