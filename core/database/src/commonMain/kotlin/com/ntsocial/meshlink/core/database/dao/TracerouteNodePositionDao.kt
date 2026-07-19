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
package com.ntsocial.meshlink.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.ntsocial.meshlink.core.database.entity.TracerouteNodePositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TracerouteNodePositionDao {

    @Query("SELECT * FROM traceroute_node_position WHERE log_uuid = :logUuid")
    fun getByLogUuid(logUuid: String): Flow<List<TracerouteNodePositionEntity>>

    @Query("DELETE FROM traceroute_node_position WHERE log_uuid = :logUuid")
    suspend fun deleteByLogUuid(logUuid: String)

    @Upsert suspend fun insertAll(entities: List<TracerouteNodePositionEntity>)
}
