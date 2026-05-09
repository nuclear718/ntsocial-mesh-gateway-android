/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.feature.messaging.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.message_delivery_status
import com.ntsocial.meshlink.core.resources.react
import com.ntsocial.meshlink.core.resources.reply
import com.ntsocial.meshlink.core.ui.emoji.EmojiPickerDialog
import com.ntsocial.meshlink.core.ui.icon.Acknowledged
import com.ntsocial.meshlink.core.ui.icon.AddLink
import com.ntsocial.meshlink.core.ui.icon.AddReaction
import com.ntsocial.meshlink.core.ui.icon.CloudUpload
import com.ntsocial.meshlink.core.ui.icon.LinkIcon
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.MessageEnroute
import com.ntsocial.meshlink.core.ui.icon.MessageError
import com.ntsocial.meshlink.core.ui.icon.MqttDelivered
import com.ntsocial.meshlink.core.ui.icon.Reply
import com.ntsocial.meshlink.core.ui.icon.Warning
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReactionButton(onSendReaction: (String) -> Unit = {}) {
    var showEmojiPickerDialog by remember { mutableStateOf(false) }
    if (showEmojiPickerDialog) {
        EmojiPickerDialog(
            onConfirm = { selectedEmoji ->
                showEmojiPickerDialog = false
                onSendReaction(selectedEmoji)
            },
            onDismiss = { showEmojiPickerDialog = false },
        )
    }
    IconButton(onClick = { showEmojiPickerDialog = true }) {
        Icon(imageVector = MeshtasticIcons.AddReaction, contentDescription = stringResource(Res.string.react))
    }
}

@Composable
private fun ReplyButton(onClick: () -> Unit = {}) = IconButton(
    onClick = onClick,
    content = { Icon(imageVector = MeshtasticIcons.Reply, contentDescription = stringResource(Res.string.reply)) },
)

@Composable
internal fun MessageStatusButton(onStatusClick: () -> Unit = {}, status: MessageStatus, fromLocal: Boolean) =
    AnimatedVisibility(visible = fromLocal) {
        IconButton(onClick = onStatusClick) {
            Crossfade(targetState = status, label = "MessageStatusIcon") { currentStatus ->
                Icon(
                    imageVector =
                    when (currentStatus) {
                        MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
                        MessageStatus.QUEUED -> MeshtasticIcons.CloudUpload
                        MessageStatus.DELIVERED -> MeshtasticIcons.MqttDelivered
                        MessageStatus.SFPP_ROUTING -> MeshtasticIcons.AddLink
                        MessageStatus.SFPP_CONFIRMED -> MeshtasticIcons.LinkIcon
                        MessageStatus.ENROUTE -> MeshtasticIcons.MessageEnroute
                        MessageStatus.ERROR -> MeshtasticIcons.MessageError
                        else -> MeshtasticIcons.Warning
                    },
                    contentDescription = stringResource(Res.string.message_delivery_status),
                )
            }
        }
    }

@Composable
internal fun MessageActions(
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
    status: MessageStatus?,
    onSendReaction: (String) -> Unit = {},
    onSendReply: () -> Unit = {},
    onStatusClick: () -> Unit = {},
) {
    Row(modifier = modifier.wrapContentSize()) {
        ReactionButton { onSendReaction(it) }
        ReplyButton { onSendReply() }
        MessageStatusButton(
            onStatusClick = onStatusClick,
            status = status ?: MessageStatus.UNKNOWN,
            fromLocal = isLocal,
        )
    }
}
