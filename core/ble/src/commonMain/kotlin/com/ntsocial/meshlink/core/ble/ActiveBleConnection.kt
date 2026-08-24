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
package com.ntsocial.meshlink.core.ble

import com.juul.kable.Peripheral
import com.ntsocial.meshlink.core.common.util.normalizeAddress
import kotlinx.atomicfu.atomic

/** Snapshot of the currently active BLE peripheral and its address, updated atomically. */
internal data class ActiveConnection(val peripheral: Peripheral, val address: String)

/**
 * Process-local registry of active BLE connections. This resolves instance mismatch issues between dynamically created
 * UI devices (scanned vs bonded) and the transport-owned connection while allowing multiple radios to remain active.
 *
 * The immutable map is replaced atomically so readers always see a consistent peripheral/address pair. Removal is
 * ownership checked: a late disconnect from an old connection cannot erase a replacement for the same address.
 */
internal object ActiveBleConnections {
    private val activeByAddress = atomic<Map<String, ActiveConnection>>(emptyMap())

    fun get(address: String): ActiveConnection? = activeByAddress.value[normalizeAddress(address)]

    fun register(connection: ActiveConnection) {
        val key = normalizeAddress(connection.address)
        update { current -> current + (key to connection) }
    }

    fun removeIfOwned(address: String, peripheral: Peripheral) {
        val key = normalizeAddress(address)
        update { current -> if (current[key]?.peripheral === peripheral) current - key else current }
    }

    private inline fun update(transform: (Map<String, ActiveConnection>) -> Map<String, ActiveConnection>) {
        while (true) {
            val current = activeByAddress.value
            val updated = transform(current)
            if (updated === current || activeByAddress.compareAndSet(current, updated)) return
        }
    }
}
