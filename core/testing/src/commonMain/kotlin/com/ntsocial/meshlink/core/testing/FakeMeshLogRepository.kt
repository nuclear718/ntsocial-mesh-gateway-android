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
package com.ntsocial.meshlink.core.testing

import com.ntsocial.meshlink.core.model.MeshLog
import com.ntsocial.meshlink.core.repository.MeshLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

/** A test double for [MeshLogRepository] that provides in-memory log storage. */
@Suppress("TooManyFunctions")
class FakeMeshLogRepository :
    BaseFake(),
    MeshLogRepository {
    private val logsFlow = mutableStateFlow<List<MeshLog>>(emptyList())
    val currentLogs: List<MeshLog>
        get() = logsFlow.value

    var lastDeletedOlderThan: Int? = null
        private set

    var deleteAllCalled = false
        private set

    override fun reset() {
        super.reset()
        lastDeletedOlderThan = null
        deleteAllCalled = false
    }

    override fun getAllLogs(maxItem: Int): Flow<List<MeshLog>> = logsFlow.map { it.take(maxItem) }

    override fun getAllLogsInReceiveOrder(maxItem: Int): Flow<List<MeshLog>> = logsFlow.map { it.take(maxItem) }

    override fun getAllLogsUnbounded(): Flow<List<MeshLog>> = logsFlow

    override fun getLogsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshLog>> =
        logsFlow.map { it.filter { log -> log.fromNum == nodeNum && log.portNum == portNum } }

    override fun getMeshPacketsFrom(nodeNum: Int, portNum: Int): Flow<List<MeshPacket>> = MutableStateFlow(emptyList())

    override fun getTelemetryFrom(nodeNum: Int): Flow<List<Telemetry>> = MutableStateFlow(emptyList())

    override fun getRequestLogs(targetNodeNum: Int, portNum: PortNum): Flow<List<MeshLog>> =
        MutableStateFlow(emptyList())

    override fun getMyNodeInfo(): Flow<MyNodeInfo?> = MutableStateFlow(null)

    override suspend fun insert(log: MeshLog) {
        logsFlow.value = logsFlow.value + log
    }

    override suspend fun deleteAll() {
        logsFlow.value = emptyList()
        deleteAllCalled = true
    }

    override suspend fun deleteLog(uuid: String) {
        logsFlow.value = logsFlow.value.filter { it.uuid != uuid }
    }

    override suspend fun deleteLogs(nodeNum: Int, portNum: Int) {
        logsFlow.value = logsFlow.value.filterNot { it.fromNum == nodeNum && it.portNum == portNum }
    }

    override suspend fun deleteLogsOlderThan(retentionDays: Int) {
        lastDeletedOlderThan = retentionDays
    }

    fun setLogs(logs: List<MeshLog>) {
        logsFlow.value = logs
    }
}
