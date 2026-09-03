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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosBarcodeScannerCoordinatorTest {
    @Test
    fun `supported host completes one scan with the exact payload`() {
        val coordinator = IosBarcodeScannerCoordinator()
        val host = FakeIosBarcodeScannerHost(isSupported = true)
        var result: String? = null

        coordinator.install(host)
        coordinator.startScan { result = it }
        coordinator.complete(host.lastRequestId, CHANNEL_URL)
        coordinator.complete(host.lastRequestId, "ignored")

        assertTrue(coordinator.isSupported)
        assertEquals(1, host.startCount)
        assertEquals(CHANNEL_URL, result)
    }

    @Test
    fun `unsupported host never starts or retains a result callback`() {
        val coordinator = IosBarcodeScannerCoordinator()
        val host = FakeIosBarcodeScannerHost(isSupported = false)
        var callbackInvoked = false

        coordinator.install(host)
        coordinator.startScan { callbackInvoked = true }
        coordinator.complete(1, CHANNEL_URL)

        assertFalse(coordinator.isSupported)
        assertEquals(0, host.startCount)
        assertFalse(callbackInvoked)
    }

    @Test
    fun `second start is ignored until cancellation completes the first`() {
        val coordinator = IosBarcodeScannerCoordinator()
        val host = FakeIosBarcodeScannerHost(isSupported = true)
        var firstResult: String? = "pending"
        var secondResult: String? = "pending"

        coordinator.install(host)
        coordinator.startScan { firstResult = it }
        coordinator.startScan { secondResult = it }
        coordinator.complete(host.lastRequestId, null)

        assertEquals(1, host.startCount)
        assertNull(firstResult)
        assertEquals("pending", secondResult)
    }

    @Test
    fun `replacement host cancels old request and stale result cannot complete new request`() {
        val coordinator = IosBarcodeScannerCoordinator()
        val oldHost = FakeIosBarcodeScannerHost(isSupported = true)
        val newHost = FakeIosBarcodeScannerHost(isSupported = true)
        var oldResult: String? = "pending"
        var newResult: String? = "pending"

        coordinator.install(oldHost)
        coordinator.startScan { oldResult = it }
        val oldRequestId = oldHost.lastRequestId
        coordinator.install(newHost)
        coordinator.startScan { newResult = it }

        assertNull(oldResult)
        coordinator.complete(oldRequestId, "stale")
        assertEquals("pending", newResult)
        coordinator.complete(newHost.lastRequestId, CHANNEL_URL)
        assertEquals(CHANNEL_URL, newResult)
    }

    @Test
    fun `uninstall cancels request only for the installed host`() {
        val coordinator = IosBarcodeScannerCoordinator()
        val host = FakeIosBarcodeScannerHost(isSupported = true)
        val otherHost = FakeIosBarcodeScannerHost(isSupported = true)
        var result: String? = "pending"

        coordinator.install(host)
        coordinator.startScan { result = it }
        coordinator.uninstall(otherHost)
        assertEquals("pending", result)

        coordinator.uninstall(host)
        assertNull(result)
        assertFalse(coordinator.isSupported)
    }

    private class FakeIosBarcodeScannerHost(override val isSupported: Boolean) : IosBarcodeScannerHost {
        var startCount = 0
        var lastRequestId = 0L

        override fun startScan(requestId: Long) {
            startCount += 1
            lastRequestId = requestId
        }
    }

    private companion object {
        const val CHANNEL_URL = "https://meshtastic.org/e/#CgMSAQESBggBQANIAQ"
    }
}
