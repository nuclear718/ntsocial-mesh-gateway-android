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
package com.ntsocial.meshlink.feature.meshcore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.common.util.NumberFormatter
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessage
import com.ntsocial.meshlink.core.meshcore.MeshCoreMessageDirection
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_person
import com.ntsocial.meshlink.core.resources.ic_send
import com.ntsocial.meshlink.core.resources.meshcore_channel_message
import com.ntsocial.meshlink.core.resources.meshcore_direct_message
import com.ntsocial.meshlink.core.resources.meshcore_hops
import com.ntsocial.meshlink.core.resources.meshcore_message_input_disabled
import com.ntsocial.meshlink.core.resources.meshcore_no_messages
import com.ntsocial.meshlink.core.resources.meshcore_snr
import com.ntsocial.meshlink.core.resources.send
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MeshCoreConversationScreen(
    viewModel: MeshCoreViewModel,
    conversationId: String,
    title: String,
    isChannel: Boolean,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = state.messagesFor(conversationId).filter { it.kindMatches(isChannel) }

    MeshCoreNtsocialVisualTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                MeshCoreHeader(
                    title = if (isChannel) "#${title.removePrefix("#")}" else title,
                    eyebrow =
                    stringResource(
                        if (isChannel) Res.string.meshcore_channel_message else Res.string.meshcore_direct_message,
                    ),
                    onNavigateUp = onNavigateUp,
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.meshcore_no_messages),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = MeshCoreMessage::id) { message -> MeshCoreMessageBubble(message) }
                    }
                }
                MeshCoreDisabledComposer()
            }
        }
    }
}

@Composable
private fun MeshCoreMessageBubble(message: MeshCoreMessage) {
    val isLocal = message.direction == MeshCoreMessageDirection.SENT
    val bubbleColor =
        if (isLocal) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val bubbleContentColor =
        if (isLocal) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val bubbleShape =
        if (isLocal) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
        }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = maxWidth * MESSAGE_BUBBLE_MAX_WIDTH_FRACTION
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (isLocal) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                MeshCoreIdentityIcon(Res.drawable.ic_person)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                color = bubbleColor,
                contentColor = bubbleContentColor,
                shape = bubbleShape,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.widthIn(max = bubbleMaxWidth).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                    )
                    val metadata = message.metadataText()
                    if (metadata.isNotEmpty()) {
                        Text(
                            text = metadata,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleContentColor.copy(alpha = MESSAGE_METADATA_ALPHA),
                        )
                    }
                }
            }

            if (isLocal) {
                Spacer(modifier = Modifier.width(8.dp))
                MeshCoreIdentityIcon(Res.drawable.ic_person)
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MeshCoreDisabledComposer() {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = COMPOSER_DIVIDER_ALPHA))
            Row(
                modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .defaultMinSize(minHeight = COMPOSER_MIN_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = "",
                        onValueChange = {},
                        enabled = false,
                        textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(Res.string.meshcore_message_input_disabled),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = COMPOSER_PLACEHOLDER_ALPHA),
                        maxLines = 2,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(enabled = false, onClick = {}, modifier = Modifier.size(COMPOSER_MIN_HEIGHT)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_send),
                        contentDescription = stringResource(Res.string.send),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeshCoreMessage.metadataText(): String {
    val parts = mutableListOf<String>()
    snrDb?.let { parts += stringResource(Res.string.meshcore_snr, NumberFormatter.format(it.toDouble(), 1)) }
    path?.hopCount?.let { parts += stringResource(Res.string.meshcore_hops, it) }
    return parts.joinToString(separator = " · ")
}

private val COMPOSER_MIN_HEIGHT = 48.dp
private const val MESSAGE_BUBBLE_MAX_WIDTH_FRACTION = 0.78f
private const val MESSAGE_METADATA_ALPHA = 0.72f
private const val COMPOSER_DIVIDER_ALPHA = 0.2f
private const val COMPOSER_PLACEHOLDER_ALPHA = 0.5f
