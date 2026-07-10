/*
 * Copyright (c) 2026 Meshtastic LLC
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NtsocialGatewayCommandCapabilityStoreTest {
    private val store = NtsocialGatewayCommandCapabilityStore()
    private val caller = NtsocialGatewayCaller(uid = 42, packageName = "com.ntsocial.android")

    @Test
    fun `capability is bound to request caller and single use`() {
        val issued = store.issue(caller, requestId = "request-1", nowMillis = 1_000L)

        assertEquals(caller, store.consume(issued.token, requestId = "request-1", senderUid = 42, nowMillis = 1_001L))
        assertNull(store.consume(issued.token, requestId = "request-1", senderUid = 42, nowMillis = 1_002L))
    }

    @Test
    fun `capability rejects mismatched request sender and expiry`() {
        val wrongRequest = store.issue(caller, requestId = "request-1", nowMillis = 1_000L)
        assertNull(store.consume(wrongRequest.token, requestId = "request-2", senderUid = 42, nowMillis = 1_001L))

        val wrongSender = store.issue(caller, requestId = "request-3", nowMillis = 1_000L)
        assertNull(store.consume(wrongSender.token, requestId = "request-3", senderUid = 99, nowMillis = 1_001L))

        val expired = store.issue(caller, requestId = "request-4", nowMillis = 1_000L)
        assertTrue(expired.expiresAtMillis > 1_000L)
        val expiry = expired.expiresAtMillis
        val consumed = store.consume(expired.token, requestId = "request-4", senderUid = null, nowMillis = expiry)
        assertNull(consumed)
    }
}
