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

import com.ntsocial.meshlink.core.ble.BleDevice
import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.ble.MeshtasticBleConstants
import com.ntsocial.meshlink.core.model.InterfaceId
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal actual fun createRadioUiPort(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    serviceRepository: ServiceRepository,
    channelOperationLock: ChannelOperationLock,
): RadioUiPort = IosRadioUiPort(
    scanner = scanner,
    bluetoothRepository = bluetoothRepository,
    radioController = radioController,
    radioInterfaceService = radioInterfaceService,
    serviceRepository = serviceRepository,
    channelOperationLock = channelOperationLock,
)

/** iOS BLE UI bridge backed by the production Kable and mesh-service owners. */
private class IosRadioUiPort(
    private val scanner: BleScanner,
    private val bluetoothRepository: BluetoothRepository,
    private val radioController: RadioController,
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val channelOperationLock: ChannelOperationLock,
) : RadioUiPort {
    private val scope = MainScope()
    private val discoveredDevices = mutableMapOf<String, RadioUiDevice>()
    private val discoveryOrder = mutableListOf<String>()
    private var scanJob: Job? = null
    private var scanGeneration = 0
    private var closed = false

    private val mutableState =
        MutableStateFlow(
            RadioUiState(
                available = true,
                hasBluetoothPermission = bluetoothRepository.state.value.hasPermissions,
                bluetoothEnabled = bluetoothRepository.state.value.enabled,
                selectedPeripheralId = radioInterfaceService.currentDeviceAddressFlow.value.toBluetoothPeripheralId(),
                connectionState = serviceRepository.connectionState.value,
                connectionProgress = serviceRepository.connectionProgress.value,
                errorMessage = serviceRepository.errorMessage.value,
            ),
        )
    override val state: StateFlow<RadioUiState> = mutableState.asStateFlow()

    init {
        observeRuntimeState()
        bluetoothRepository.refreshState()
    }

    override fun refreshBluetooth() {
        if (!closed) bluetoothRepository.refreshState()
    }

    override fun startScan() {
        if (closed || scanJob?.isActive == true) return

        bluetoothRepository.refreshState()
        val bluetoothState = bluetoothRepository.state.value
        if (!bluetoothState.hasPermissions || !bluetoothState.enabled) return

        discoveredDevices.clear()
        discoveryOrder.clear()
        val generation = ++scanGeneration
        mutableState.update { current -> current.copy(scanning = true, devices = emptyList()) }
        scanJob =
            scope.launch {
                try {
                    scanner
                        .scan(timeout = SCAN_DURATION, serviceUuid = MeshtasticBleConstants.SERVICE_UUID)
                        .catch { throwable ->
                            throwable.message?.trim()?.takeIf(String::isNotEmpty)?.let { message ->
                                serviceRepository.setErrorMessage(message)
                            }
                        }
                        .collect(::recordDevice)
                } finally {
                    if (scanGeneration == generation) {
                        scanJob = null
                        mutableState.update { current -> current.copy(scanning = false) }
                    }
                }
            }
    }

    override fun stopScan() {
        scanGeneration += 1
        scanJob?.cancel()
        scanJob = null
        mutableState.update { current -> current.copy(scanning = false) }
    }

    override fun connect(peripheralId: String) {
        if (closed) return
        val address = peripheralId.trim()
        if (!bluetoothRepository.isValid(address)) return

        stopScan()
        scope.launch {
            channelOperationLock.withLock {
                if (!closed) {
                    radioController.setDeviceAddressAndAwait(
                        radioInterfaceService.toInterfaceAddress(InterfaceId.BLUETOOTH, address),
                    )
                }
            }
        }
    }

    override fun disconnect() {
        if (closed) return
        stopScan()
        scope.launch { channelOperationLock.withLock { if (!closed) radioInterfaceService.disconnect() } }
    }

    override fun forget() {
        if (closed) return
        stopScan()
        scope.launch {
            channelOperationLock.withLock {
                if (!closed) {
                    radioController.setDeviceAddressAndAwait(
                        radioInterfaceService.toInterfaceAddress(InterfaceId.NOP, ""),
                    )
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stopScan()
        scope.cancel()
    }

    private fun observeRuntimeState() {
        bluetoothRepository.state
            .onEach { bluetoothState ->
                if (!bluetoothState.hasPermissions || !bluetoothState.enabled) stopScan()
                mutableState.update { current ->
                    current.copy(
                        hasBluetoothPermission = bluetoothState.hasPermissions,
                        bluetoothEnabled = bluetoothState.enabled,
                    )
                }
            }
            .launchIn(scope)
        radioInterfaceService.currentDeviceAddressFlow
            .onEach { address ->
                mutableState.update { current ->
                    current.copy(selectedPeripheralId = address.toBluetoothPeripheralId())
                }
            }
            .launchIn(scope)
        serviceRepository.connectionState
            .onEach { connectionState ->
                mutableState.update { current -> current.copy(connectionState = connectionState) }
            }
            .launchIn(scope)
        serviceRepository.connectionProgress
            .onEach { progress -> mutableState.update { current -> current.copy(connectionProgress = progress) } }
            .launchIn(scope)
        serviceRepository.errorMessage
            .onEach { error -> mutableState.update { current -> current.copy(errorMessage = error) } }
            .launchIn(scope)
    }

    private fun recordDevice(device: BleDevice) {
        val peripheralId = device.address.trim()
        if (peripheralId.isEmpty()) return

        val identity = peripheralId.uppercase()
        val existing = discoveredDevices[identity]
        val usefulName = device.name?.takeIf(String::isNotBlank) ?: existing?.name
        discoveredDevices[identity] = RadioUiDevice(name = usefulName, peripheralId = peripheralId, rssi = device.rssi)
        if (identity !in discoveryOrder) discoveryOrder += identity
        mutableState.update { current -> current.copy(devices = discoveryOrder.mapNotNull(discoveredDevices::get)) }
    }
}

private fun String?.toBluetoothPeripheralId(): String? =
    this?.takeIf { address -> address.firstOrNull() == InterfaceId.BLUETOOTH.id }?.drop(1)?.takeIf(String::isNotEmpty)

private val SCAN_DURATION = 20.seconds
