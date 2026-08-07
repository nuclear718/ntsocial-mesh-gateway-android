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
import com.ntsocial.meshlink.core.database.entity.MyNodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeWithRelations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.core.annotation.Single

@Single
class SwitchingNodeInfoReadDataSource(private val dbManager: DatabaseProvider) : NodeInfoReadDataSource {

    override fun myNodeInfoFlow(): Flow<MyNodeEntity?> =
        dbManager.currentDb.flatMapLatest { db -> db.nodeInfoDao().getMyNodeInfo() }

    override fun nodeDBbyNumFlow(): Flow<Map<Int, NodeWithRelations>> =
        dbManager.currentDb.flatMapLatest { db -> db.nodeInfoDao().nodeDBbyNum() }

    override suspend fun readCurrentSnapshot(): CurrentNodeDataSnapshot = requireNotNull(
        dbManager.withDb { db ->
            val dao = db.nodeInfoDao()
            CurrentNodeDataSnapshot(
                nodesByNumber = dao.nodeDBbyNum().first(),
                myNodeInfo = dao.getMyNodeInfo().first(),
            )
        },
    ) {
        "Active radio database is unavailable"
    }

    override fun getNodesFlow(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        hopsAwayMax: Int,
        lastHeardMin: Int,
    ): Flow<List<NodeWithRelations>> = dbManager.currentDb.flatMapLatest { db ->
        db.nodeInfoDao()
            .getNodes(
                sort = sort,
                filter = filter,
                includeUnknown = includeUnknown,
                hopsAwayMax = hopsAwayMax,
                lastHeardMin = lastHeardMin,
            )
    }

    override suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity> =
        dbManager.withDb { it.nodeInfoDao().getNodesOlderThan(lastHeard) } ?: emptyList()

    override suspend fun getUnknownNodes(): List<NodeEntity> =
        dbManager.withDb { it.nodeInfoDao().getUnknownNodes() } ?: emptyList()
}
