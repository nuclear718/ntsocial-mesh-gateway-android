/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.ntsocial.meshlink.core.database.entity.DeviceLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceLinkDao {
    @Upsert suspend fun upsertAll(links: List<DeviceLinkEntity>)

    @Query("SELECT * FROM device_link ORDER BY short_code")
    fun observeAll(): Flow<List<DeviceLinkEntity>>

    @Query("SELECT * FROM device_link ORDER BY short_code")
    suspend fun getAll(): List<DeviceLinkEntity>

    @Query("DELETE FROM device_link WHERE short_code NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<String>)

    @Query("DELETE FROM device_link")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM device_link")
    suspend fun count(): Int
}
