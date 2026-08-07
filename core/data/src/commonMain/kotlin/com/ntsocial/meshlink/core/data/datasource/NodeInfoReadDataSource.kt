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

import com.ntsocial.meshlink.core.database.entity.MyNodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeWithRelations
import kotlinx.coroutines.flow.Flow

/** Nodes and local-radio metadata read from one captured database instance. */
data class CurrentNodeDataSnapshot(val nodesByNumber: Map<Int, NodeWithRelations>, val myNodeInfo: MyNodeEntity?)

interface NodeInfoReadDataSource {
    fun myNodeInfoFlow(): Flow<MyNodeEntity?>

    fun nodeDBbyNumFlow(): Flow<Map<Int, NodeWithRelations>>

    /**
     * Reads the database that is active when this call begins without consulting the derived [Flow] projections.
     * Implementations must read both values from the same captured database instance.
     */
    suspend fun readCurrentSnapshot(): CurrentNodeDataSnapshot

    fun getNodesFlow(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        hopsAwayMax: Int,
        lastHeardMin: Int,
    ): Flow<List<NodeWithRelations>>

    suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity>

    suspend fun getUnknownNodes(): List<NodeEntity>
}
