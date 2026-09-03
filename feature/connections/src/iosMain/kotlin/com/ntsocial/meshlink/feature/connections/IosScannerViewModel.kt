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
package com.ntsocial.meshlink.feature.connections

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.ntsocial.meshlink.core.ble.BleDevice
import com.ntsocial.meshlink.core.ble.BlePairingException
import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.ble.MeshtasticBleDevice
import com.ntsocial.meshlink.core.common.util.safeCatching
import com.ntsocial.meshlink.core.datastore.RecentAddressesDataSource
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.network.repository.NetworkRepository
import com.ntsocial.meshlink.core.radiofleet.DiscoveredRadio
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.UiPrefs
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import com.ntsocial.meshlink.feature.connections.model.GetDiscoveredDevicesUseCase
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/** Apple binding for the shared Bluetooth-only Connections experience. */
@KoinViewModel(binds = [ScannerViewModel::class])
@Suppress("LongParameterList")
class IosScannerViewModel(
    serviceRepository: ServiceRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    radioPrefs: RadioPrefs,
    recentAddressesDataSource: RecentAddressesDataSource,
    getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
    networkRepository: NetworkRepository,
    dispatchers: CoroutineDispatchers,
    uiPrefs: UiPrefs,
    bleScanner: BleScanner? = null,
    private val bluetoothRepository: BluetoothRepository,
    private val radioFleetManager: RadioFleetManager,
) : ScannerViewModel(
    serviceRepository,
    radioController,
    radioInterfaceService,
    radioPrefs,
    recentAddressesDataSource,
    getDiscoveredDevicesUseCase,
    networkRepository,
    dispatchers,
    uiPrefs,
    bleScanner,
) {
    override fun requestBonding(entry: DeviceListEntry.Ble) {
        requestBluetoothPairingGuidance(deviceName = entry.name) {
            viewModelScope.launch { pairAndConnect(device = entry.device) { connectSelectedNow(entry) } }
        }
    }

    /** Routes reconnects from an existing iOS fleet card through the same first-pairing preparation. */
    fun requestFleetConnection(endpointId: RadioEndpointId, deviceName: String) {
        requestBluetoothPairingGuidance(deviceName = deviceName) {
            viewModelScope.launch {
                val profile = radioFleetManager.snapshots.value[endpointId]?.profile ?: return@launch
                radioFleetManager.disconnect(endpointId)
                val address = profile.transportAddress.removePrefix("x").removePrefix("!")
                val device = MeshtasticBleDevice(address = address, name = deviceName, reconnectByIdentifier = true)
                pairAndConnect(device = device) { radioFleetManager.connect(endpointId) }
            }
        }
    }

    private suspend fun pairAndConnect(device: BleDevice, onPaired: suspend () -> Unit) {
        try {
            bluetoothRepository.bond(device)
            onPaired()
        } catch (pairing: BlePairingException) {
            Logger.w(pairing) { "iOS native Bluetooth pairing did not complete" }
            serviceRepository.setErrorMessage(pairing.message.orEmpty(), Severity.Warn)
        } finally {
            bluetoothRepository.discardPreparedDevice(device)
        }
    }

    override fun connectSelected(entry: DeviceListEntry) {
        viewModelScope.launch { connectSelectedNow(entry) }
    }

    private suspend fun connectSelectedNow(entry: DeviceListEntry) {
        addRecentAddress(entry.fullAddress, entry.name)
        safeCatching {
            val profile =
                radioFleetManager.register(
                    candidate = DiscoveredRadio(transportAddress = entry.fullAddress, displayName = entry.name),
                    connect = false,
                )
            // A prior failed first-pairing attempt can leave the primary transport in Connecting. Stop it before
            // handing the already-connected pairing peripheral to the authoritative endpoint session.
            radioFleetManager.disconnect(profile.id)
            if (profile.legacyPrimary) {
                radioPrefs.setDevName(entry.name)
                changeDeviceAddress(entry.fullAddress)
            }
            radioFleetManager.connect(profile.id)
        }
            .onFailure { error ->
                Logger.w(error) { "Unable to add iOS Meshtastic endpoint" }
                serviceRepository.setErrorMessage(
                    text = error.message ?: "Unable to add Meshtastic endpoint",
                    severity = Severity.Warn,
                )
            }
    }

    override fun disconnect() {
        val primary = radioFleetManager.snapshots.value.values.firstOrNull { it.profile.legacyPrimary }
        if (primary == null) {
            super.disconnect()
        } else {
            viewModelScope.launch { radioFleetManager.disconnect(primary.profile.id) }
        }
    }
}
