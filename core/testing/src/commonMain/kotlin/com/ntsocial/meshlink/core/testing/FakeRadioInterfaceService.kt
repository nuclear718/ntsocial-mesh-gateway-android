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
package com.ntsocial.meshlink.core.testing

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.model.InterfaceId
import com.ntsocial.meshlink.core.model.MeshActivity
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A test double for [RadioInterfaceService] that provides an in-memory implementation.
 *
 * The [connectionState] here mirrors the transport-level semantics of the real implementation. In production, only
 * [MeshConnectionManager][com.ntsocial.meshlink.core.repository.MeshConnectionManager] observes this flow; tests should
 * verify that bridging behavior rather than consuming it directly from UI/feature test code (use
 * [FakeServiceRepository.connectionState] instead).
 */
@Suppress("TooManyFunctions")
class FakeRadioInterfaceService(override val serviceScope: CoroutineScope = MainScope()) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType> = emptyList()

    /** Transport-level connection state (raw hardware link status). */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(null)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow

    private val _radioSessionState =
        MutableStateFlow(
            RadioSessionState(
                epoch = 1,
                selectedDeviceAddress = null,
                activeDeviceAddress = null,
                transportConnectionState = ConnectionState.Disconnected,
                configured = false,
            ),
        )
    override val radioSessionState: StateFlow<RadioSessionState> = _radioSessionState

    // Use an unbounded Channel to mirror SharedRadioInterfaceService semantics. A MutableSharedFlow would
    // hide the stop/start backlog bug that motivated the resetReceivedBuffer() API.
    private val _receivedData = Channel<ByteArray>(Channel.UNLIMITED)
    override val receivedData: Flow<ByteArray> = _receivedData.receiveAsFlow()

    private val _meshActivity = MutableSharedFlow<MeshActivity>()
    override val meshActivity: Flow<MeshActivity> = _meshActivity.asFlow()

    private val _connectionError = MutableSharedFlow<String>()
    override val connectionError: Flow<String> = _connectionError.asFlow()

    val sentToRadio = mutableListOf<ByteArray>()
    var connectCalled = false

    override fun isMockTransport(): Boolean = true

    override fun sendToRadio(bytes: ByteArray) {
        sentToRadio.add(bytes)
    }

    override fun sendToRadioForSession(bytes: ByteArray, expectedRadioSessionEpoch: Long): Boolean {
        val session = radioSessionState.value
        return if (session.epoch == expectedRadioSessionEpoch && session.isConfiguredReady) {
            sendToRadio(bytes)
            true
        } else {
            false
        }
    }

    override fun connect() {
        connectCalled = true
        nextSession(activeDeviceAddress = getDeviceAddress(), connectionState = ConnectionState.Connecting)
    }

    override suspend fun disconnect() {
        connectCalled = false
        nextSession(activeDeviceAddress = null, connectionState = ConnectionState.Disconnected)
    }

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        _currentDeviceAddressFlow.value = deviceAddr
        nextSession(
            selectedDeviceAddress = deviceAddr,
            activeDeviceAddress = null,
            connectionState = ConnectionState.Disconnected,
        )
        return true
    }

    override fun markCurrentSessionConfigured(expectedEpoch: Long): Boolean {
        val current = _radioSessionState.value
        return if (
            current.epoch == expectedEpoch &&
            current.selectedDeviceAddress != null &&
            current.selectedDeviceAddress == current.activeDeviceAddress &&
            current.transportConnectionState == ConnectionState.Connected
        ) {
            _radioSessionState.value = current.copy(configured = true)
            true
        } else {
            false
        }
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "$interfaceId:$rest"

    override fun onConnect() {
        _connectionState.value = ConnectionState.Connected
        nextSession(activeDeviceAddress = getDeviceAddress(), connectionState = ConnectionState.Connected)
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        _connectionState.value = ConnectionState.Disconnected
        nextSession(
            activeDeviceAddress = _radioSessionState.value.activeDeviceAddress,
            connectionState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep,
        )
    }

    override fun handleFromRadio(bytes: ByteArray) {
        _receivedData.trySend(bytes)
    }

    override fun resetReceivedBuffer() {
        @Suppress("EmptyWhileBlock", "ControlFlowWithEmptyBody")
        while (_receivedData.tryReceive().isSuccess) Unit
    }

    // --- Helper methods for testing ---

    fun emitFromRadio(bytes: ByteArray) {
        _receivedData.trySend(bytes)
    }

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    private fun nextSession(
        selectedDeviceAddress: String? = _currentDeviceAddressFlow.value,
        activeDeviceAddress: String?,
        connectionState: ConnectionState,
    ) {
        val current = _radioSessionState.value
        _radioSessionState.value =
            RadioSessionState(
                epoch = current.epoch + 1,
                selectedDeviceAddress = selectedDeviceAddress,
                activeDeviceAddress = activeDeviceAddress,
                transportConnectionState = connectionState,
                configured = false,
            )
    }
}
