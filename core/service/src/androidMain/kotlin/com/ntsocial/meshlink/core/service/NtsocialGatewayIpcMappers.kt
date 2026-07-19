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

import com.ntsocial.meshlink.core.gateway.NtsocialEnvelopeData
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayStatus
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport

internal fun NtsocialCachedEnvelope.toGatewayEnvelopeData(): NtsocialEnvelopeData = NtsocialEnvelopeData(
    direction = direction.name,
    version = envelope.version,
    headerMsgId = envelope.headerMsgId.toByteArray(),
    payload = envelope.payload.toByteArray(),
    rawBytes = rawBytes.toByteArray(),
    packetId = packetId,
    from = from,
    to = to,
    channelIndex = channelIndex,
    portNum = portNum,
    cachedAtMillis = cachedAtMillis,
)

internal fun buildNtsocialGatewayStatus(
    connectionState: ConnectionState,
    cachedEnvelopeCount: Int,
): NtsocialGatewayStatus = NtsocialGatewayStatus(
    connectionState = connectionState.toGatewayStatusName(),
    cachedEnvelopeCount = cachedEnvelopeCount,
    privateAppPortNum = NtsocialTransport.PRIVATE_APP_PORT_NUM,
    legacyReceiveOnlyPortNum = NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM,
    maxEnvelopeSizeBytes = NtsocialTransport.MAX_ENVELOPE_SIZE_BYTES,
    maxPayloadSizeBytes = NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES,
)

private fun ConnectionState.toGatewayStatusName(): String = when (this) {
    ConnectionState.Connected -> "CONNECTED"
    ConnectionState.Connecting -> "CONNECTING"
    ConnectionState.DeviceSleep -> "DEVICE_SLEEP"
    ConnectionState.Disconnected -> "DISCONNECTED"
}
