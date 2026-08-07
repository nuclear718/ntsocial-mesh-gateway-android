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
@file:Suppress("TooManyFunctions")

package com.ntsocial.meshlink.core.repository

import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio

/** Interface for handling the transmission of packets to the radio and managing the packet queue. */
interface PacketHandler {
    /** Sends a command/packet directly to the radio. */
    fun sendToRadio(p: ToRadio)

    /** Sends a raw control frame only through the exact configured radio session. */
    fun sendToRadioForSession(p: ToRadio, expectedRadioSessionEpoch: Long): Boolean = false

    /** Adds a mesh packet to the queue for sending. */
    fun sendToRadio(packet: MeshPacket)

    /**
     * Adds a mesh packet to the queue and suspends until the radio acknowledges it via [QueueStatus].
     *
     * Unlike [sendToRadio], which is fire-and-forget, this method provides back-pressure so the caller can ensure a
     * packet has been accepted by the radio before proceeding. This is critical for operations where ordering matters
     * (e.g., sending a shared contact before the first DM).
     *
     * @return `true` if the radio accepted the packet, `false` on timeout or failure.
     */
    suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean

    /**
     * Atomically admits [packet] only while [expectedRadioSessionEpoch] still owns the open outbound generation. The
     * production implementation linearizes the exact-session check with queue stop/resume admission so a delayed
     * coroutine from a retired radio cannot enqueue into its replacement radio's generation.
     */
    suspend fun sendToRadioAndAwaitForSession(packet: MeshPacket, expectedRadioSessionEpoch: Long): Boolean =
        sendToRadioAndAwait(packet)

    /** Retains the durable Gateway source identity until exact-session radio queue admission completes. */
    suspend fun sendToRadioAndAwaitForGatewaySession(
        packet: MeshPacket,
        expectedRadioSessionEpoch: Long,
        expectedSourceChannelId: String,
    ): Boolean = sendToRadioAndAwaitForSession(packet, expectedRadioSessionEpoch)

    /** Gateway-specific worker-owned validation and QueueStatus result at the actual dispatch boundary. */
    suspend fun dispatchGatewayPacketAndAwait(
        packet: MeshPacket,
        expectedRadioSessionEpoch: Long,
        expectedSourceChannelId: String,
    ): GatewayPacketDispatchResult =
        if (sendToRadioAndAwaitForGatewaySession(packet, expectedRadioSessionEpoch, expectedSourceChannelId)) {
            GatewayPacketDispatchResult.ACCEPTED
        } else {
            GatewayPacketDispatchResult.TRANSIENT_FAILURE
        }

    /** Processes queue status updates from the radio. */
    fun handleQueueStatus(queueStatus: QueueStatus)

    /** Removes a pending response for a request. */
    fun removeResponse(dataRequestId: Int, complete: Boolean)

    /** Stops the packet queue. */
    fun stopPacketQueue()

    /** Stops the packet queue and waits until its worker and pending response state are fully retired. */
    suspend fun stopPacketQueueAndAwait() {
        stopPacketQueue()
    }

    /** Reopens the current outbound generation after a replacement transport reaches Connected. */
    suspend fun resumePacketQueueAndAwait() = Unit
}

enum class GatewayPacketDispatchResult {
    ACCEPTED,
    SOURCE_IDENTITY_MISMATCH,
    TRANSIENT_FAILURE,
}
