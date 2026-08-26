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
package com.ntsocial.meshlink.core.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NtsocialGatewayFleetFacadeTest {
    @Test
    fun `message progress does not invalidate route fleet generation`() {
        val initial = endpoint(messageChangeSeq = 1)
        assertEquals(
            NtsocialGatewayFleetFacade.fleetGeneration(listOf(initial)),
            NtsocialGatewayFleetFacade.fleetGeneration(
                listOf(initial.copy(messageChangeSeq = 2, sessionState = "DEGRADED")),
            ),
        )
        assertNotEquals(
            NtsocialGatewayFleetFacade.fleetGeneration(listOf(initial)),
            NtsocialGatewayFleetFacade.fleetGeneration(listOf(initial.copy(endpointGeneration = "generation-b"))),
        )
    }

    private fun endpoint(messageChangeSeq: Long) = NtsocialGatewayEndpointSnapshot(
        endpointId = "endpoint-a",
        displayName = "Alpha",
        addressSuffix = null,
        protocol = "BLE",
        sessionState = "READY",
        endpointGeneration = "generation-a",
        catalogGeneration = "catalog-a",
        historyEpoch = "history-a",
        messageChangeSeq = messageChangeSeq,
        nativeHistoryAvailable = true,
        nativeTextSendAvailable = true,
        arbitraryRouteOverlayAvailable = true,
        hasCachedCatalog = true,
        appearanceToken = null,
        sortOrder = 0,
        channels = emptyList(),
    )
}
