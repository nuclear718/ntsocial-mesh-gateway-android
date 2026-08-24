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
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ActiveBleConnectionsTest {
    @Test
    fun `connections are address scoped and stale removal cannot clobber replacement`() {
        val peripheralA = mock<Peripheral>(MockMode.autofill)
        val replacementA = mock<Peripheral>(MockMode.autofill)
        val peripheralB = mock<Peripheral>(MockMode.autofill)

        ActiveBleConnections.register(ActiveConnection(peripheralA, ADDRESS_A))
        ActiveBleConnections.register(ActiveConnection(peripheralB, ADDRESS_B))
        ActiveBleConnections.register(ActiveConnection(replacementA, ADDRESS_A.lowercase()))

        assertSame(replacementA, ActiveBleConnections.get(ADDRESS_A)?.peripheral)
        assertSame(peripheralB, ActiveBleConnections.get(ADDRESS_B)?.peripheral)

        ActiveBleConnections.removeIfOwned(ADDRESS_A, peripheralA)
        assertSame(replacementA, ActiveBleConnections.get(ADDRESS_A)?.peripheral)

        ActiveBleConnections.removeIfOwned(ADDRESS_A, replacementA)
        ActiveBleConnections.removeIfOwned(ADDRESS_B, peripheralB)
        assertNull(ActiveBleConnections.get(ADDRESS_A))
        assertNull(ActiveBleConnections.get(ADDRESS_B))
    }

    private companion object {
        const val ADDRESS_A = "AA:BB:CC:DD:EE:01"
        const val ADDRESS_B = "AA:BB:CC:DD:EE:02"
    }
}
