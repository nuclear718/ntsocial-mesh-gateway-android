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

import com.ntsocial.meshlink.core.database.entity.MetadataEntity
import com.ntsocial.meshlink.core.database.entity.MyNodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeEntity

interface NodeInfoWriteDataSource {
    suspend fun upsert(node: NodeEntity)

    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>)

    suspend fun clearNodeDB(preserveFavorites: Boolean)

    suspend fun clearMyNodeInfo()

    suspend fun deleteNode(num: Int)

    suspend fun deleteNodes(nodeNums: List<Int>)

    suspend fun deleteMetadata(num: Int)

    suspend fun upsert(metadata: MetadataEntity)

    suspend fun setNodeNotes(num: Int, notes: String)

    suspend fun backfillDenormalizedNames()
}
