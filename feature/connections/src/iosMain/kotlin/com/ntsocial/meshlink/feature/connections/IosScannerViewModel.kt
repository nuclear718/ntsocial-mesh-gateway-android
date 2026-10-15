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
import com.ntsocial.meshlink.core.radiofleet.RadioConnectionPreparation
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
            viewModelScope.launch { connectSelectedNow(entry, entry.device) }
        }
    }

    /** Routes reconnects from an existing iOS fleet card through the same first-pairing preparation. */
    fun requestFleetConnection(endpointId: RadioEndpointId, deviceName: String) {
        requestBluetoothPairingGuidance(deviceName = deviceName) {
            viewModelScope.launch {
                val profile =
                    radioFleetManager.rememberedProfiles.value.firstOrNull { it.id == endpointId } ?: return@launch
                val address = profile.transportAddress.removePrefix("x").removePrefix("!")
                val device = MeshtasticBleDevice(address = address, name = deviceName, reconnectByIdentifier = true)
                connectPrepared(
                    DiscoveredRadio(transportAddress = profile.transportAddress, displayName = deviceName),
                    device,
                )
            }
        }
    }

    override fun connectSelected(entry: DeviceListEntry) {
        viewModelScope.launch { connectSelectedNow(entry) }
    }

    private suspend fun connectSelectedNow(entry: DeviceListEntry, device: BleDevice? = null) {
        addRecentAddress(entry.fullAddress, entry.name)
        connectPrepared(DiscoveredRadio(transportAddress = entry.fullAddress, displayName = entry.name), device)
    }

    private suspend fun connectPrepared(candidate: DiscoveredRadio, device: BleDevice?) {
        safeCatching {
            // Admission precedes native pairing: a prepared peripheral consumes the same four-radio quota.
            val profile = radioFleetManager.register(candidate, connect = false)
            val runtime = checkNotNull(radioFleetManager.snapshots.value[profile.id]).runtimeId
            val preparation = device?.let { pairingPreparation(it, runtime) }
            radioFleetManager.connect(profile.id, preparation)
        }
            .onFailure { error ->
                Logger.w(error) { "Unable to add iOS Meshtastic endpoint" }
                serviceRepository.setErrorMessage(error.message.orEmpty(), Severity.Warn)
            }
    }

    private fun pairingPreparation(device: BleDevice, runtime: String) = object : RadioConnectionPreparation {
        override val runtimeId = runtime

        override suspend fun prepare() {
            try {
                bluetoothRepository.bond(device)
            } catch (pairing: BlePairingException) {
                serviceRepository.setErrorMessage(pairing.message.orEmpty(), Severity.Warn)
                throw pairing
            }
        }

        override suspend fun close() {
            bluetoothRepository.discardPreparedDevice(device)
        }
    }

    override fun disconnect() {
        val endpointId = radioFleetManager.selectedEndpointId.value ?: return
        viewModelScope.launch { radioFleetManager.disconnectAndRelease(endpointId) }
    }
}
