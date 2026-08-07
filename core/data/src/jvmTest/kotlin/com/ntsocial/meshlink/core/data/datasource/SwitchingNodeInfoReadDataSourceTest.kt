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
import com.ntsocial.meshlink.core.database.MeshtasticDatabase
import com.ntsocial.meshlink.core.database.entity.MyNodeEntity
import com.ntsocial.meshlink.core.database.entity.NodeEntity
import com.ntsocial.meshlink.core.database.getInMemoryDatabaseBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

class SwitchingNodeInfoReadDataSourceTest {

    @Test
    fun `raw snapshot ignores stale derived database and reads one active instance`() = runTest {
        val staleDb = getInMemoryDatabaseBuilder().build()
        val activeDb = getInMemoryDatabaseBuilder().build()
        try {
            staleDb.nodeInfoDao().installConfig(myNodeInfo(11), listOf(NodeEntity(num = 11, user = User(id = "!old"))))
            activeDb.nodeInfoDao().installConfig(myNodeInfo(22), listOf(NodeEntity(num = 22, user = User(id = "!new"))))

            val provider = StaleProjectionDatabaseProvider(staleProjection = staleDb, rawActive = activeDb)
            val snapshot = SwitchingNodeInfoReadDataSource(provider).readCurrentSnapshot()

            assertEquals(setOf(22), snapshot.nodesByNumber.keys)
            assertEquals("!new", snapshot.nodesByNumber.getValue(22).node.user.id)
            assertEquals(22, snapshot.myNodeInfo?.myNodeNum)
            assertEquals(1, provider.rawSnapshotReads)
        } finally {
            staleDb.close()
            activeDb.close()
        }
    }

    private fun myNodeInfo(nodeNum: Int) = MyNodeEntity(
        myNodeNum = nodeNum,
        model = "test",
        firmwareVersion = "1.0",
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 8,
        hasWifi = false,
    )

    private class StaleProjectionDatabaseProvider(
        staleProjection: MeshtasticDatabase,
        private val rawActive: MeshtasticDatabase,
    ) : DatabaseProvider {
        override val currentDb: StateFlow<MeshtasticDatabase> = MutableStateFlow(staleProjection)
        var rawSnapshotReads: Int = 0
            private set

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T {
            rawSnapshotReads += 1
            return block(rawActive)
        }
    }
}
