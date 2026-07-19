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
import com.ntsocial.meshlink.core.database.entity.MetadataEntity
import com.ntsocial.meshlink.core.database.entity.MyNodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeEntity
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class SwitchingNodeInfoWriteDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) : NodeInfoWriteDataSource {

    override suspend fun upsert(node: NodeEntity) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().upsert(node) } }
    }

    override suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().installConfig(mi, nodes) } }
    }

    override suspend fun clearNodeDB(preserveFavorites: Boolean) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().clearNodeInfo(preserveFavorites) } }
    }

    override suspend fun clearMyNodeInfo() {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().clearMyNodeInfo() } }
    }

    override suspend fun deleteNode(num: Int) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().deleteNode(num) } }
    }

    override suspend fun deleteNodes(nodeNums: List<Int>) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().deleteNodes(nodeNums) } }
    }

    override suspend fun deleteMetadata(num: Int) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().deleteMetadata(num) } }
    }

    override suspend fun upsert(metadata: MetadataEntity) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().upsert(metadata) } }
    }

    override suspend fun setNodeNotes(num: Int, notes: String) {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().setNodeNotes(num, notes) } }
    }

    override suspend fun backfillDenormalizedNames() {
        withContext(dispatchers.io) { dbManager.withDb { it.nodeInfoDao().backfillDenormalizedNames() } }
    }
}
