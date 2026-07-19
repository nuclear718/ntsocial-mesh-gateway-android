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
package com.ntsocial.meshlink.feature.connections.domain.usecase

import android.hardware.usb.UsbManager
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.datastore.RecentAddressesDataSource
import com.ntsocial.meshlink.core.datastore.model.RecentAddress
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.network.repository.DiscoveredService
import com.ntsocial.meshlink.core.network.repository.UsbRepository
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.demo_mode
import com.ntsocial.meshlink.core.resources.meshtastic
import com.ntsocial.meshlink.feature.connections.model.AndroidUsbDeviceData
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import com.ntsocial.meshlink.feature.connections.model.DiscoveredDevices
import com.ntsocial.meshlink.feature.connections.model.GetDiscoveredDevicesUseCase
import com.ntsocial.meshlink.feature.connections.model.getMeshtasticShortName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import java.util.Locale

@Suppress("LongParameterList")
@Single(binds = [GetDiscoveredDevicesUseCase::class])
class AndroidGetDiscoveredDevicesUseCase(
    private val bluetoothRepository: BluetoothRepository,
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val nodeRepository: NodeRepository,
    private val databaseManager: DatabaseManager,
    private val usbRepository: UsbRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val usbManagerLazy: Lazy<UsbManager>,
) : GetDiscoveredDevicesUseCase {
    private val macSuffixLength = 8

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun invoke(showMock: Boolean, resolvedList: Flow<List<DiscoveredService>>): Flow<DiscoveredDevices> {
        val nodeDb = nodeRepository.nodeDBbyNum

        // Filter out non-Meshtastic peripherals (headphones, cars, watches, etc.).
        // BluetoothAdapter.bondedDevices returns every bonded device on the phone, so we
        // must restrict the picker to entries whose advertised name matches the
        // Meshtastic firmware pattern (see MeshtasticBleConstants.BLE_NAME_PATTERN).
        val bondedBleFlow =
            bluetoothRepository.state.map { ble ->
                ble.bondedDevices.filter { it.getMeshtasticShortName() != null }.map { DeviceListEntry.Ble(it) }
            }

        val processedTcpFlow =
            combine(resolvedList, recentAddressesDataSource.recentAddresses) { tcpServices, recentList ->
                val defaultName = getString(Res.string.meshtastic)
                processTcpServices(tcpServices, recentList, defaultName)
            }

        val usbDevicesFlow =
            usbRepository.serialDevices.map { usb ->
                usb.map { (_, d) ->
                    DeviceListEntry.Usb(
                        usbData = AndroidUsbDeviceData(d),
                        name = d.device.deviceName,
                        fullAddress =
                        radioInterfaceService.toInterfaceAddress(
                            com.ntsocial.meshlink.core.model.InterfaceId.SERIAL,
                            d.device.deviceName,
                        ),
                        bonded = usbManagerLazy.value.hasPermission(d.device),
                    )
                }
            }

        return combine(
            nodeDb,
            bondedBleFlow,
            processedTcpFlow,
            usbDevicesFlow,
            resolvedList,
            recentAddressesDataSource.recentAddresses,
        ) { args: Array<Any> ->
            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val db = args[0] as Map<Int, Node>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val bondedBle = args[1] as List<DeviceListEntry.Ble>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val processedTcp = args[2] as List<DeviceListEntry.Tcp>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val usbDevices = args[3] as List<DeviceListEntry.Usb>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val resolved = args[4] as List<DiscoveredService>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val recentList = args[5] as List<RecentAddress>

            val bleForUi = matchBleNodes(bondedBle, db)
            val usbForUi = matchUsbNodes(usbDevices, showMock, db)

            val discoveredTcpForUi = matchDiscoveredTcpNodes(processedTcp, db, resolved, databaseManager)
            val discoveredTcpAddresses = processedTcp.map { it.fullAddress }.toSet()
            val recentTcpForUi = buildRecentTcpEntries(recentList, discoveredTcpAddresses, db, databaseManager)

            DiscoveredDevices(
                bleDevices = bleForUi,
                usbDevices = usbForUi,
                discoveredTcpDevices = discoveredTcpForUi,
                recentTcpDevices = recentTcpForUi,
            )
        }
    }

    private fun matchBleNodes(bondedBle: List<DeviceListEntry.Ble>, db: Map<Int, Node>): List<DeviceListEntry.Ble> =
        bondedBle
            .map { entry ->
                val matchingNode =
                    if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                        db.values.find { node ->
                            val macSuffix =
                                entry.device.address.replace(":", "").takeLast(macSuffixLength).lowercase(Locale.ROOT)
                            val nameSuffix = entry.device.getMeshtasticShortName()?.lowercase(Locale.ROOT)
                            node.user.id.lowercase(Locale.ROOT).endsWith(macSuffix) ||
                                (nameSuffix != null && node.user.id.lowercase(Locale.ROOT).endsWith(nameSuffix))
                        }
                    } else {
                        null
                    }
                entry.copy(node = matchingNode)
            }
            .sortedBy { it.name }

    private suspend fun matchUsbNodes(
        usbDevices: List<DeviceListEntry.Usb>,
        showMock: Boolean,
        db: Map<Int, Node>,
    ): List<DeviceListEntry> =
        (usbDevices + if (showMock) listOf(DeviceListEntry.Mock(getString(Res.string.demo_mode))) else emptyList())
            .map { entry ->
                entry.copy(node = findNodeByNameSuffix(entry.name, entry.fullAddress, db, databaseManager))
            }
}
