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
package com.ntsocial.meshlink.core.repository

import org.meshtastic.proto.MeshPacket

/** Interface for processing incoming radio messages and mesh packets. */
interface MeshMessageProcessor {
    /** Handles a raw message received from the radio. */
    fun handleFromRadio(bytes: ByteArray, myNodeNum: Int?)

    /** Handles a received mesh packet. */
    fun handleReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?)

    /** Clears the buffer of early received packets. */
    fun clearEarlyPackets()

    /** Clears the early-packet buffer and does not return until the mutation is complete. */
    suspend fun clearEarlyPacketsAndAwait() {
        clearEarlyPackets()
    }

    /** Pauses ingress and waits for all work owned by the retired radio generation. */
    suspend fun quiesceIngress() {
        clearEarlyPacketsAndAwait()
    }

    /** Resumes ingress after the replacement database and transport are ready. */
    fun resumeIngress() = Unit
}
