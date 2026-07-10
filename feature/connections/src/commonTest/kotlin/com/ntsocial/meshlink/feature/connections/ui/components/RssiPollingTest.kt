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
package com.ntsocial.meshlink.feature.connections.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RssiPollingTest {

    @Test
    fun `successful RSSI reads retain the normal polling cadence`() {
        assertEquals(5.seconds, rssiPollDelay(consecutiveFailures = 0))
    }

    @Test
    fun `failed RSSI reads back off and cap the retry delay`() {
        assertEquals(5.seconds, rssiPollDelay(consecutiveFailures = 1))
        assertEquals(10.seconds, rssiPollDelay(consecutiveFailures = 2))
        assertEquals(20.seconds, rssiPollDelay(consecutiveFailures = 3))
        assertEquals(30.seconds, rssiPollDelay(consecutiveFailures = 4))
        assertEquals(30.seconds, rssiPollDelay(consecutiveFailures = 100))
    }
}
