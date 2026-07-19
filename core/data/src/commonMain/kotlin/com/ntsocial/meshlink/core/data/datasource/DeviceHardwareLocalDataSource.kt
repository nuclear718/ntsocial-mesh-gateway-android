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
package com.ntsocial.meshlink.core.data.datasource

import com.ntsocial.meshlink.core.database.DatabaseProvider
import com.ntsocial.meshlink.core.database.entity.DeviceHardwareEntity
import com.ntsocial.meshlink.core.database.entity.asEntity
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.NetworkDeviceHardware
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class DeviceHardwareLocalDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    private val deviceHardwareDao
        get() = dbManager.currentDb.value.deviceHardwareDao()

    suspend fun insertAllDeviceHardware(deviceHardware: List<NetworkDeviceHardware>) =
        withContext(dispatchers.io) { deviceHardwareDao.insertAll(deviceHardware.map { it.asEntity() }) }

    suspend fun deleteAllDeviceHardware() = withContext(dispatchers.io) { deviceHardwareDao.deleteAll() }

    suspend fun getByHwModel(hwModel: Int): List<DeviceHardwareEntity> =
        withContext(dispatchers.io) { deviceHardwareDao.getByHwModel(hwModel) }

    suspend fun getByTarget(target: String): DeviceHardwareEntity? =
        withContext(dispatchers.io) { deviceHardwareDao.getByTarget(target) }

    suspend fun getByModelAndTarget(hwModel: Int, target: String): DeviceHardwareEntity? =
        withContext(dispatchers.io) { deviceHardwareDao.getByModelAndTarget(hwModel, target) }
}
