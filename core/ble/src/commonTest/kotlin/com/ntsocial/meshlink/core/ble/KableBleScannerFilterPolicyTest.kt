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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class KableBleScannerFilterPolicyTest {
    @Test
    fun `Apple keeps service filter and omits unsupported address filter`() {
        val plan = kableScanFilterPlan(SERVICE_UUID, SAVED_PERIPHERAL_UUID, supportsAddressFilter = false)

        assertEquals(KableScanFilterPlan(includeService = true, includeAddress = false), plan)
    }

    @Test
    fun `Android and Desktop retain service and address OR filters`() {
        val plan = kableScanFilterPlan(SERVICE_UUID, SAVED_PERIPHERAL_UUID, supportsAddressFilter = true)

        assertEquals(KableScanFilterPlan(includeService = true, includeAddress = true), plan)
    }

    @Test
    fun `unsupported address filter is omitted even without service filter`() {
        val plan =
            kableScanFilterPlan(serviceUuid = null, address = SAVED_PERIPHERAL_UUID, supportsAddressFilter = false)

        assertEquals(KableScanFilterPlan(includeService = false, includeAddress = false), plan)
    }

    private companion object {
        val SERVICE_UUID: Uuid = MeshtasticBleConstants.SERVICE_UUID
        const val SAVED_PERIPHERAL_UUID = "12345678-1234-5678-9abc-def012345678"
    }
}
