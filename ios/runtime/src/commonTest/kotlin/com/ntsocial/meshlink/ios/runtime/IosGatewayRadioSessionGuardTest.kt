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
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayReadiness
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.repository.RadioSessionState
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosGatewayRadioSessionGuardTest {
    @Test
    fun `selected radio is not ready until its active session finishes configuration`() {
        val switching = guard(session(epoch = 8, selected = RADIO_B, active = RADIO_A, configured = true))
        val connecting = guard(session(epoch = 9, selected = RADIO_B, active = RADIO_B, configured = false))
        val ready = guard(session(epoch = 9, selected = RADIO_B, active = RADIO_B, configured = true))

        assertEquals(AppleGatewayReadiness.DISCONNECTED, switching.readiness(hasChannels = true))
        assertEquals(AppleGatewayReadiness.CONFIGURING, connecting.readiness(hasChannels = true))
        assertEquals(AppleGatewayReadiness.READY, ready.readiness(hasChannels = true))
    }

    @Test
    fun `disconnect reconnect epoch invalidates otherwise identical routing context`() {
        val first = guard(session(epoch = 12, selected = RADIO_A, active = RADIO_A, configured = true))
        val reconnected = guard(session(epoch = 15, selected = RADIO_A, active = RADIO_A, configured = true))

        assertNotEquals(first, reconnected)
        assertNotEquals(first.routingContext(), reconnected.routingContext())
    }

    @Test
    fun `Bluetooth loss is fail closed and changes routing context`() {
        val radioSession = session(epoch = 4, selected = RADIO_A, active = RADIO_A, configured = true)
        val ready = guard(radioSession)
        val disabled =
            iosGatewayRadioSessionGuard(
                session = radioSession,
                databaseDeviceAddress = RADIO_A,
                bluetooth = BluetoothState(hasPermissions = true, enabled = false),
                appConnectionState = ConnectionState.Connected,
                channelSetFingerprint = CHANNEL_FINGERPRINT,
            )

        assertEquals(AppleGatewayReadiness.BLUETOOTH_UNAVAILABLE, disabled.readiness(hasChannels = true))
        assertNotEquals(ready.routingContext(), disabled.routingContext())
    }

    @Test
    fun `configured transport cannot become ready against a different active database`() {
        val radioSession = session(epoch = 21, selected = RADIO_B, active = RADIO_B, configured = true)
        val matching = guard(radioSession, databaseDeviceAddress = RADIO_B)
        val staleDatabase = guard(radioSession, databaseDeviceAddress = RADIO_A)

        assertEquals(AppleGatewayReadiness.READY, matching.readiness(hasChannels = true))
        assertEquals(AppleGatewayReadiness.CONFIGURING, staleDatabase.readiness(hasChannels = true))
        assertNotEquals(matching.routingContext(), staleDatabase.routingContext())
    }

    private fun guard(
        session: RadioSessionState,
        databaseDeviceAddress: String? = session.selectedDeviceAddress,
    ): IosGatewayRadioSessionGuard = iosGatewayRadioSessionGuard(
        session = session,
        databaseDeviceAddress = databaseDeviceAddress,
        bluetooth = BluetoothState(hasPermissions = true, enabled = true),
        appConnectionState = ConnectionState.Connected,
        channelSetFingerprint = CHANNEL_FINGERPRINT,
    )

    private fun session(epoch: Long, selected: String, active: String, configured: Boolean) = RadioSessionState(
        epoch = epoch,
        selectedDeviceAddress = selected,
        activeDeviceAddress = active,
        transportConnectionState = ConnectionState.Connected,
        configured = configured,
    )

    private companion object {
        const val RADIO_A = "xAAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
        const val RADIO_B = "xBBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB"
        val CHANNEL_FINGERPRINT = "channels".encodeUtf8().sha256()
    }
}
