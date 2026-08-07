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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BoundedGatewayRetrySchedulerTest {
    @Test
    fun `duplicate wakeups coalesce and retry stops after success`() = runTest {
        var calls = 0
        val scheduler =
            BoundedGatewayRetryScheduler(
                scope = backgroundScope,
                maxAttempts = 4,
                delayMillisForAttempt = { attempt -> attempt * 100L },
                retry = { ++calls < 3 },
            )

        scheduler.schedule()
        scheduler.schedule()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(3, calls)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, calls)
    }

    @Test
    fun `persistent failure is bounded by maximum attempts`() = runTest {
        var calls = 0
        val scheduler =
            BoundedGatewayRetryScheduler(
                scope = backgroundScope,
                maxAttempts = 3,
                delayMillisForAttempt = { 10L },
                retry = {
                    calls += 1
                    true
                },
            )

        scheduler.schedule()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(3, calls)
    }

    @Test
    fun `retry never runs inline even when configured delay is zero`() = runTest {
        var calls = 0
        val scheduler =
            BoundedGatewayRetryScheduler(
                scope = backgroundScope,
                maxAttempts = 1,
                delayMillisForAttempt = { 0L },
                retry = {
                    calls += 1
                    false
                },
            )

        scheduler.schedule()
        runCurrent()
        assertEquals(0, calls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, calls)
    }
}
