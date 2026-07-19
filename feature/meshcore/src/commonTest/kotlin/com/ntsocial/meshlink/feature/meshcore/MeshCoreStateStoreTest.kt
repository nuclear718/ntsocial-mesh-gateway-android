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
package com.ntsocial.meshlink.feature.meshcore

import com.ntsocial.meshlink.core.meshcore.MeshCoreChannel
import com.ntsocial.meshlink.core.meshcore.MeshCoreConnectionState
import com.ntsocial.meshlink.core.meshcore.MeshCoreContact
import com.ntsocial.meshlink.core.meshcore.MeshCoreContactType
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessage
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessageDirection
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessageKind
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessageStatus
import com.ntsocial.meshlink.core.meshcore.MeshCorePath
import com.ntsocial.meshlink.core.meshcore.MeshCorePathMode
import com.ntsocial.meshlink.core.meshcore.MeshCoreTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MeshCoreStateStoreTest {
    @Test
    fun `section selection does not depend on Meshtastic state`() {
        val store = MeshCoreStateStore()

        store.selectSection(MeshCoreSection.RADIO)

        assertEquals(MeshCoreSection.RADIO, store.state.value.selectedSection)
        assertEquals(MeshCoreConnectionState.DISCONNECTED, store.state.value.connectionState)
    }

    @Test
    fun `snapshot creates independent contact and channel conversations`() {
        val store = MeshCoreStateStore()
        val publicKey = ByteArray(32) { it.toByte() }
        val contactId = contactConversationId(publicKey)
        val channelId = channelConversationId(1)
        val directMessage = message("direct", contactId, MeshCoreMessageKind.DIRECT, 10)
        val channelMessage = message("channel", channelId, MeshCoreMessageKind.CHANNEL, 20)

        store.replaceSnapshot(
            connectionState = MeshCoreConnectionState.CONNECTED,
            activeTransport = MeshCoreTransport.BLE,
            transportAvailable = true,
            selfInfo = null,
            deviceInfo = null,
            contacts = listOf(contact(publicKey)),
            channels = listOf(MeshCoreChannel(1, "#ntsocial", ByteArray(16))),
            messages = listOf(directMessage, channelMessage),
        )

        val state = store.state.value
        assertEquals(listOf("#ntsocial", "Alice"), state.conversations.map(MeshCoreConversation::title))
        assertEquals(listOf(directMessage), state.messagesFor(contactId))
        assertEquals(listOf(channelMessage), state.messagesFor(channelId))
        assertFalse(state.conversations.first { it.id == contactId }.isChannel)
    }
}

private fun contact(publicKey: ByteArray) = MeshCoreContact(
    publicKey = publicKey,
    type = MeshCoreContactType.CHAT,
    flags = 0,
    outboundPath = MeshCorePath(MeshCorePathMode.ROUTED, 1, 1, 1),
    outboundPathBytes = byteArrayOf(1),
    name = "Alice",
    lastAdvertEpochSeconds = 0,
    advertisedLatitudeE6 = 0,
    advertisedLongitudeE6 = 0,
    lastModifiedEpochSeconds = 0,
)

private fun message(id: String, conversationId: String, kind: MeshCoreMessageKind, timestamp: Long) = MeshCoreMessage(
    id = id,
    conversationId = conversationId,
    kind = kind,
    text = id,
    senderTimestamp = timestamp,
    direction = MeshCoreMessageDirection.RECEIVED,
    status = MeshCoreMessageStatus.RECEIVED,
    path = null,
    snrDb = null,
    signed = false,
)
