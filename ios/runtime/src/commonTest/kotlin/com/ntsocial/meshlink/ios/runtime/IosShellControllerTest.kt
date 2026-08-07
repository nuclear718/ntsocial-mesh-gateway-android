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

import com.ntsocial.meshlink.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosShellControllerTest {
    @Test
    fun `radio state and commands are delegated without synthetic connection state`() {
        val port = FakeRadioUiPort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = IosShellController(radioUiPort = port, scope = scope)

        try {
            val radioState =
                RadioUiState(
                    available = true,
                    hasBluetoothPermission = true,
                    bluetoothEnabled = true,
                    devices = listOf(RadioUiDevice("MeshLink radio", RADIO_ID, -48)),
                    selectedPeripheralId = RADIO_ID,
                    connectionState = ConnectionState.Connecting,
                    connectionProgress = "Requesting configuration",
                    errorMessage = "Canonical service error",
                )
            port.emit(radioState)

            assertEquals(radioState, controller.state.value.radio)
            controller.refreshBluetooth()
            controller.toggleRadioScan()
            controller.connectRadio(RADIO_ID)
            controller.disconnectRadio()
            controller.forgetRadio()

            assertEquals(1, port.refreshCalls)
            assertEquals(1, port.startScanCalls)
            assertEquals(RADIO_ID, port.connectedPeripheralId)
            assertEquals(1, port.disconnectCalls)
            assertEquals(1, port.forgetCalls)

            port.emit(radioState.copy(scanning = true))
            controller.toggleRadioScan()
            assertEquals(1, port.stopScanCalls)
        } finally {
            controller.close()
            scope.cancel()
        }
        assertTrue(port.closed)
    }

    @Test
    fun `host and parent handoff state remain independent from radio facts`() {
        val port = FakeRadioUiPort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = IosShellController(radioUiPort = port, scope = scope)

        try {
            controller.setHostActive(true)
            controller.handleOpenUrl("ntsocial-meshlink://process")

            assertTrue(controller.state.value.hostActive)
            assertEquals(ParentHandoffState.ACCEPTED, controller.state.value.parentHandoffState)
            assertFalse(controller.state.value.radio.available)

            controller.handleOpenUrl("ntsocial-meshlink://gateway")
            assertEquals(ParentHandoffState.REJECTED, controller.state.value.parentHandoffState)

            controller.handleOpenUrl("ntsocial-meshlink://process?retry=1")
            assertEquals(ParentHandoffState.REJECTED, controller.state.value.parentHandoffState)

            controller.handleOpenUrl("ntsocial-meshlink://process/")
            assertEquals(ParentHandoffState.REJECTED, controller.state.value.parentHandoffState)

            controller.handleOpenUrl("https://example.invalid")
            assertEquals(ParentHandoffState.REJECTED, controller.state.value.parentHandoffState)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    private companion object {
        const val RADIO_ID = "CAFECAFE-1234-5678-9ABC-0123456789AB"
    }
}

private class FakeRadioUiPort : RadioUiPort {
    private val mutableState = MutableStateFlow(RadioUiState())
    override val state: StateFlow<RadioUiState> = mutableState.asStateFlow()

    var refreshCalls = 0
        private set

    var startScanCalls = 0
        private set

    var stopScanCalls = 0
        private set

    var connectedPeripheralId: String? = null
        private set

    var disconnectCalls = 0
        private set

    var forgetCalls = 0
        private set

    var closed = false
        private set

    fun emit(state: RadioUiState) {
        mutableState.value = state
    }

    override fun refreshBluetooth() {
        refreshCalls += 1
    }

    override fun startScan() {
        startScanCalls += 1
    }

    override fun stopScan() {
        stopScanCalls += 1
    }

    override fun connect(peripheralId: String) {
        connectedPeripheralId = peripheralId
    }

    override fun disconnect() {
        disconnectCalls += 1
    }

    override fun forget() {
        forgetCalls += 1
    }

    override fun close() {
        closed = true
    }
}
