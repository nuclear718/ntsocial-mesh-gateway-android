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

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString
import org.meshtastic.proto.MeshPacket

/** NTsocial Gateway MVP data plane for PRIVATE_APP envelopes and cache access. */
interface NtsocialGatewayRepository {
    val cachedEnvelopes: StateFlow<List<NtsocialCachedEnvelope>>

    /** Latest canonical NTsocial-channel readiness result, populated after node DB readiness. */
    val defaultChannelStatus: StateFlow<NtsocialDefaultChannelStatus>

    /**
     * Validates and caches an inbound NTsocial envelope. Returns true when the packet is a valid NTsocial envelope,
     * including duplicates that were already cached.
     */
    fun cacheInbound(packet: MeshPacket, dataPacket: DataPacket): Boolean

    /**
     * Sends a small NTsocial test payload through Meshtastic PRIVATE_APP / port 256 and caches the outbound envelope.
     */
    fun sendTestPayload(
        payload: ByteString,
        to: String? = DataPacket.ID_BROADCAST,
        channelIndex: Int = 0,
        wantAck: Boolean = true,
        headerMsgId: ByteString? = null,
    ): NtsocialCachedEnvelope

    /**
     * Queues an already encoded NTsocial envelope without wrapping it again.
     *
     * The external IPC command boundary uses this path so the parent application's `NM + version + header + payload`
     * envelope remains byte-for-byte stable. New radio sends are always PRIVATE_APP / port 256.
     */
    fun sendRawEnvelope(
        rawEnvelope: ByteString,
        to: String? = DataPacket.ID_BROADCAST,
        channelIndex: Int,
        hopLimit: Int = 0,
        wantAck: Boolean = true,
        packetId: Int? = null,
    ): NtsocialCachedEnvelope

    /**
     * Persists an encoded NTsocial envelope as a queued packet and commits platform retry work before returning.
     *
     * Repeating the same [packetId] and packet body is safe. A packet-ID collision with different content fails closed.
     * Successful return proves only local durable queue admission, never RF or remote delivery.
     */
    suspend fun persistAndQueueRawEnvelope(
        rawEnvelope: ByteString,
        to: String? = DataPacket.ID_BROADCAST,
        channelIndex: Int,
        hopLimit: Int = 0,
        wantAck: Boolean = true,
        packetId: Int,
    ): NtsocialCachedEnvelope

    /** Updates the ephemeral provisioning/readiness snapshot exposed through the Android gateway provider. */
    fun updateDefaultChannelStatus(status: NtsocialDefaultChannelStatus)

    fun clearCache()
}
