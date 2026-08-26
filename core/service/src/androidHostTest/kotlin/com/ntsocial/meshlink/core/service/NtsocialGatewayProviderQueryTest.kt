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

import android.net.Uri
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageChange
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageIdentity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NtsocialGatewayProviderQueryTest {

    @Test
    fun `provider query parser accepts only bounded unambiguous decimal cursors`() {
        assertEquals(
            GatewayMessageChangesQuery(after = 7, limit = 200),
            parseGatewayMessageChangesQuery(
                Uri.parse("content://com.ntsocial.meshlink.gateway/v2/message-changes?after=7&limit=200"),
            ),
        )
        assertEquals(
            GatewayMessageChangesQuery(after = 0, limit = 100),
            parseGatewayMessageChangesQuery(Uri.parse("content://com.ntsocial.meshlink.gateway/v2/message-changes")),
        )

        listOf("after=-1", "after=not-a-number", "limit=0", "limit=201", "limit=abc", "unknown=1", "after=1&after=2")
            .forEach { query ->
                assertFailsWith<IllegalArgumentException> {
                    parseGatewayMessageChangesQuery(
                        Uri.parse("content://com.ntsocial.meshlink.gateway/v2/message-changes?$query"),
                    )
                }
            }
    }

    @Test
    fun `v3 query parser requires one endpoint id`() {
        assertEquals(
            GatewayV3MessageChangesQuery(endpointId = "endpoint-a", after = 3, limit = 50),
            parseGatewayV3MessageChangesQuery(
                Uri.parse(
                    "content://com.ntsocial.meshlink.gateway/v3/message-changes" +
                        "?endpoint_id=endpoint-a&after=3&limit=50",
                ),
            ),
        )
        listOf(
            "after=0",
            "endpoint_id=bad endpoint",
            "endpoint_id=endpoint-a&endpoint_id=endpoint-b",
        ).forEach { query ->
            assertFailsWith<IllegalArgumentException> {
                parseGatewayV3MessageChangesQuery(
                    Uri.parse("content://com.ntsocial.meshlink.gateway/v3/message-changes?$query"),
                )
            }
        }
    }

    @Test
    fun `message change projection exports origin client id only as nullable correlation metadata`() {
        val identity =
            NtsocialGatewayMessageIdentity(
                sourceChannelId = "meshtastic:source",
                sourceMessageId = "0123456789ABCDEF0123456789ABCDEF",
            )
        val packet =
            DataPacket(to = DataPacket.ID_BROADCAST, channel = 1, text = "native").apply {
                from = "!12345678"
                id = -1
                status = MessageStatus.QUEUED
                time = 123L
            }
        val originClientMessageId = "ABCDEF0123456789ABCDEF0123456789"
        val values =
            gatewayMessageChangeValues(
                change =
                NtsocialGatewayMessageChange(
                    changeSeq = 9,
                    identity = identity,
                    packet = packet,
                    receivedAtMillis = 456L,
                    originClientMessageId = originClientMessageId,
                ),
                identity = identity,
                fromNodeId = "!12345678",
                fromDisplayName = "Local",
                localNodeId = "!12345678",
            )

        assertEquals(originClientMessageId, values[NtsocialGatewayContract.COLUMN_ORIGIN_CLIENT_MESSAGE_ID])
        assertEquals(0xFFFF_FFFFL, values[NtsocialGatewayContract.COLUMN_PACKET_ID])
        assertEquals("OUTBOUND", values[NtsocialGatewayContract.COLUMN_DIRECTION])
        assertEquals("native", values[NtsocialGatewayContract.COLUMN_TEXT])
    }
}
