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

import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.datastore.RecentAddressesDataSource
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.feature.connections.model.GetDiscoveredDevicesUseCase
import org.koin.core.annotation.Single

/**
 * JVM/Desktop binding for [com.ntsocial.meshlink.feature.connections.model.GetDiscoveredDevicesUseCase].
 *
 * The common use-case body lives in [CommonGetDiscoveredDevicesUseCase] (un-annotated, so it does not collide with the
 * Android impl). This thin subclass registers it with Koin only for JVM/Desktop targets, where [JvmUsbScanner] supplies
 * the USB data source.
 *
 * The explicit `binds` is required because Koin annotations only infer interface bindings from directly-implemented
 * interfaces — the [GetDiscoveredDevicesUseCase] interface is implemented on the parent
 * [CommonGetDiscoveredDevicesUseCase], which the annotation processor does not walk.
 */
@Single(binds = [GetDiscoveredDevicesUseCase::class])
class JvmGetDiscoveredDevicesUseCase(
    recentAddressesDataSource: RecentAddressesDataSource,
    nodeRepository: NodeRepository,
    databaseManager: DatabaseManager,
    usbScanner: UsbScanner? = null,
) : CommonGetDiscoveredDevicesUseCase(recentAddressesDataSource, nodeRepository, databaseManager, usbScanner)
