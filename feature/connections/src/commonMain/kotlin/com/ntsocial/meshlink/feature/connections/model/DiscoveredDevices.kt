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
package com.ntsocial.meshlink.feature.connections.model

import com.ntsocial.meshlink.core.network.repository.DiscoveredService
import kotlinx.coroutines.flow.Flow

data class DiscoveredDevices(
    val bleDevices: List<DeviceListEntry> = emptyList(),
    val usbDevices: List<DeviceListEntry> = emptyList(),
    val discoveredTcpDevices: List<DeviceListEntry> = emptyList(),
    val recentTcpDevices: List<DeviceListEntry> = emptyList(),
)

interface GetDiscoveredDevicesUseCase {
    /**
     * Returns a flow of all discovered devices (BLE, USB, TCP).
     *
     * @param resolvedList the NSD/mDNS resolved services flow. On Android 15+, subscribing to
     *   `NetworkRepository.resolvedList` triggers a system consent dialog, so callers should pass `flowOf(emptyList())`
     *   unless the user has explicitly requested a network scan.
     */
    fun invoke(showMock: Boolean, resolvedList: Flow<List<DiscoveredService>>): Flow<DiscoveredDevices>
}
