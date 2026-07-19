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

import com.ntsocial.meshlink.core.datastore.RecentAddressesDataSource
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.network.repository.NetworkRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.UiPrefs
import com.ntsocial.meshlink.feature.connections.model.GetDiscoveredDevicesUseCase
import org.koin.core.annotation.KoinViewModel

/**
 * Desktop/JVM [ScannerViewModel] registration.
 *
 * On Desktop, the base [ScannerViewModel] is used directly. The default [requestBonding] connects without explicit
 * bonding since the OS Bluetooth stack handles pairing during the GATT connection.
 */
@KoinViewModel(binds = [ScannerViewModel::class])
@Suppress("LongParameterList")
class JvmScannerViewModel(
    serviceRepository: ServiceRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    radioPrefs: RadioPrefs,
    recentAddressesDataSource: RecentAddressesDataSource,
    getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
    networkRepository: NetworkRepository,
    dispatchers: com.ntsocial.meshlink.core.di.CoroutineDispatchers,
    uiPrefs: UiPrefs,
    bleScanner: com.ntsocial.meshlink.core.ble.BleScanner? = null,
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
)
