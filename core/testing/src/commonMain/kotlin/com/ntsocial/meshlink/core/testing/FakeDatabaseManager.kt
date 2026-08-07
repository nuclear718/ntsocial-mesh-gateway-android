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

import com.ntsocial.meshlink.core.common.database.DatabaseManager
import kotlinx.coroutines.flow.StateFlow

/** A test double for [DatabaseManager] that provides a simple implementation and tracks calls. */
class FakeDatabaseManager :
    BaseFake(),
    DatabaseManager {
    private val _cacheLimit = mutableStateFlow(DEFAULT_CACHE_LIMIT)
    override val cacheLimit: StateFlow<Int> = _cacheLimit
    private val _currentAddress = mutableStateFlow<String?>(null)
    override val currentAddress: StateFlow<String?> = _currentAddress

    var lastSwitchedAddress: String? = null
    val existingDatabases = mutableSetOf<String>()

    init {
        registerResetAction {
            _cacheLimit.value = DEFAULT_CACHE_LIMIT
            _currentAddress.value = null
            lastSwitchedAddress = null
            existingDatabases.clear()
        }
    }

    override fun getCurrentCacheLimit(): Int = _cacheLimit.value

    override fun setCacheLimit(limit: Int) {
        _cacheLimit.value = limit
    }

    override suspend fun switchActiveDatabase(address: String?) {
        lastSwitchedAddress = address
        _currentAddress.value = address
    }

    override fun hasDatabaseFor(address: String?): Boolean = address != null && existingDatabases.contains(address)

    /** Direct setup seam for tests that need an already-selected active database without launching a coroutine. */
    fun setCurrentAddressForTest(address: String?) {
        lastSwitchedAddress = address
        _currentAddress.value = address
    }

    companion object {
        private const val DEFAULT_CACHE_LIMIT = 100
    }
}
