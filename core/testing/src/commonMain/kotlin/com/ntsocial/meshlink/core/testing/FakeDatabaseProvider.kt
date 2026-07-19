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

import com.ntsocial.meshlink.core.database.DatabaseProvider
import com.ntsocial.meshlink.core.database.MeshtasticDatabase
import com.ntsocial.meshlink.core.database.getInMemoryDatabaseBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A real [DatabaseProvider] that uses an in-memory database for testing. */
class FakeDatabaseProvider : DatabaseProvider {
    private val db: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()
    private val _currentDb = MutableStateFlow(db)
    override val currentDb: StateFlow<MeshtasticDatabase> = _currentDb

    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(db)

    fun close() {
        db.close()
    }
}
