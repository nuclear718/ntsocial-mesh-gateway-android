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
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.ntsocial.meshlink.core.database.entity.QuickChatAction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickChatActionDao {

    @Query("Select * from quick_chat order by position asc")
    fun getAll(): Flow<List<QuickChatAction>>

    @Upsert suspend fun upsert(action: QuickChatAction)

    @Query("Delete from quick_chat")
    suspend fun deleteAll()

    @Query("Delete from quick_chat where uuid=:uuid")
    suspend fun delete(uuid: Long)

    @Transaction
    suspend fun delete(action: QuickChatAction) {
        delete(action.uuid)
        decrementPositionsAfter(action.position)
    }

    @Query("Update quick_chat set position=:position WHERE uuid=:uuid")
    suspend fun updateActionPosition(uuid: Long, position: Int)

    @Query("Update quick_chat set position=position-1 where position>=:position")
    suspend fun decrementPositionsAfter(position: Int)
}
