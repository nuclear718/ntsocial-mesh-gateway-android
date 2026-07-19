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
import com.ntsocial.meshlink.core.meshcore.MeshCoreDeviceInfo
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessage
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessageKind
import com.ntsocial.meshlink.core.meshcore.MeshCoreSelfInfo
import com.ntsocial.meshlink.core.meshcore.MeshCoreTransport

enum class MeshCoreSection {
    MESSAGES,
    CONTACTS_AND_CHANNELS,
    RADIO,
}

data class MeshCoreConversation(
    val id: String,
    val title: String,
    val isChannel: Boolean,
    val lastMessage: MeshCoreMessage?,
)

data class MeshCoreUiState(
    val selectedSection: MeshCoreSection = MeshCoreSection.MESSAGES,
    val connectionState: MeshCoreConnectionState = MeshCoreConnectionState.DISCONNECTED,
    val activeTransport: MeshCoreTransport? = null,
    val transportAvailable: Boolean = false,
    val selfInfo: MeshCoreSelfInfo? = null,
    val deviceInfo: MeshCoreDeviceInfo? = null,
    val contacts: List<MeshCoreContact> = emptyList(),
    val channels: List<MeshCoreChannel> = emptyList(),
    val messages: List<MeshCoreMessage> = emptyList(),
) {
    val conversations: List<MeshCoreConversation>
        get() {
            val contactConversations =
                contacts.map { contact ->
                    val conversationId = contactConversationId(contact.publicKey)
                    MeshCoreConversation(
                        id = conversationId,
                        title = contact.name.ifBlank { conversationId.removePrefix(CONTACT_PREFIX) },
                        isChannel = false,
                        lastMessage = messages.lastOrNull { it.conversationId == conversationId },
                    )
                }
            val channelConversations =
                channels
                    .filter { it.name.isNotBlank() }
                    .map { channel ->
                        val conversationId = channelConversationId(channel.index)
                        MeshCoreConversation(
                            id = conversationId,
                            title = channel.name,
                            isChannel = true,
                            lastMessage = messages.lastOrNull { it.conversationId == conversationId },
                        )
                    }
            return (contactConversations + channelConversations).sortedWith(
                compareByDescending<MeshCoreConversation> { it.lastMessage?.senderTimestamp ?: 0L }.thenBy { it.title },
            )
        }

    fun messagesFor(conversationId: String): List<MeshCoreMessage> =
        messages.filter { it.conversationId == conversationId }.sortedBy(MeshCoreMessage::senderTimestamp)
}

fun contactConversationId(publicKey: ByteArray): String =
    CONTACT_PREFIX + publicKey.take(PUBLIC_KEY_PREFIX_BYTES).joinToString(separator = "") { byte -> byte.toHex() }

fun channelConversationId(channelIndex: Int): String = "$CHANNEL_PREFIX$channelIndex"

fun MeshCoreMessage.kindMatches(isChannel: Boolean): Boolean = (kind == MeshCoreMessageKind.CHANNEL) == isChannel

private fun Byte.toHex(): String = (toInt() and UNSIGNED_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')

private const val CONTACT_PREFIX = "contact:"
private const val CHANNEL_PREFIX = "channel:"
private const val PUBLIC_KEY_PREFIX_BYTES = 6
private const val HEX_RADIX = 16
private const val HEX_BYTE_WIDTH = 2
private const val UNSIGNED_BYTE_MASK = 0xFF
