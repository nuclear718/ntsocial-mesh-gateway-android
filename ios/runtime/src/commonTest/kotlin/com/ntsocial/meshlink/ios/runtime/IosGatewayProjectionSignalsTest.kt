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

import com.ntsocial.meshlink.core.ble.BluetoothState
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.repository.RadioSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class IosGatewayProjectionSignalsTest {
    @Test
    fun `inbound activation revision alone emits the ready projection drain signal`() = runTest {
        val inboundRevision = MutableStateFlow(0L)
        var drainCount = 0
        val collector = launch {
            iosGatewayProjectionSignals(
                bluetooth = MutableStateFlow(BluetoothState(hasPermissions = true, enabled = true)),
                connection = MutableStateFlow(ConnectionState.Connected),
                session = MutableStateFlow(readySession()),
                databaseDeviceAddress = MutableStateFlow(RADIO),
                channels = MutableStateFlow(ChannelSet(settings = listOf(ChannelSettings(name = "primary")))),
                channelSnapshotGeneration = MutableStateFlow(2L),
                inboundSessionRevision = inboundRevision,
            )
                .take(2)
                .collect { signal ->
                    if (signal.inboundSessionRevision > 0 && signal.session.configured) drainCount += 1
                }
        }
        runCurrent()

        inboundRevision.value = 1L
        collector.join()

        assertEquals(1, drainCount)
    }

    private fun readySession() = RadioSessionState(
        epoch = 9,
        selectedDeviceAddress = RADIO,
        activeDeviceAddress = RADIO,
        transportConnectionState = ConnectionState.Connected,
        configured = true,
    )

    private companion object {
        const val RADIO = "xAAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
    }
}
