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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_chat_bubble_outline
import com.ntsocial.meshlink.core.resources.ic_forum
import com.ntsocial.meshlink.core.resources.ic_person
import com.ntsocial.meshlink.core.resources.ic_warning
import com.ntsocial.meshlink.core.resources.meshcore
import com.ntsocial.meshlink.core.resources.meshcore_channel_message
import com.ntsocial.meshlink.core.resources.meshcore_contacts_channels
import com.ntsocial.meshlink.core.resources.meshcore_direct_message
import com.ntsocial.meshlink.core.resources.meshcore_independent_system
import com.ntsocial.meshlink.core.resources.meshcore_messages
import com.ntsocial.meshlink.core.resources.meshcore_no_conversations
import com.ntsocial.meshlink.core.resources.meshcore_phase_one
import com.ntsocial.meshlink.core.resources.meshcore_radio
import com.ntsocial.meshlink.core.resources.meshcore_setting_unavailable
import com.ntsocial.meshlink.core.resources.meshcore_transport_pending_description
import com.ntsocial.meshlink.core.resources.meshcore_transport_pending_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MeshCoreScreen(
    viewModel: MeshCoreViewModel,
    onOpenConversation: (MeshCoreConversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MeshCoreNtsocialVisualTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                MeshCoreHeader(
                    title = stringResource(Res.string.meshcore),
                    eyebrow = stringResource(Res.string.meshcore_independent_system),
                    isFeatureRoot = true,
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                MeshCorePhaseCard(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                MeshCoreSectionTabs(selected = state.selectedSection, onSelect = viewModel::selectSection)
                Box(modifier = Modifier.fillMaxSize()) {
                    when (state.selectedSection) {
                        MeshCoreSection.MESSAGES ->
                            MeshCoreMessagesPanel(state = state, onOpenConversation = onOpenConversation)

                        MeshCoreSection.CONTACTS_AND_CHANNELS -> MeshCoreDirectoryPanel(state)

                        MeshCoreSection.RADIO -> MeshCoreRadioPanel(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeshCorePhaseCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.meshcore_transport_pending_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MeshCorePhaseBadge()
                }
                Text(
                    text = stringResource(Res.string.meshcore_transport_pending_description),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MeshCorePhaseBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.meshcore_phase_one),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MeshCoreSectionTabs(selected: MeshCoreSection, onSelect: (MeshCoreSection) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        MeshCoreSection.entries.forEach { section ->
            Tab(
                selected = section == selected,
                onClick = { onSelect(section) },
                text = {
                    Text(text = stringResource(section.labelResource()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun MeshCoreMessagesPanel(state: MeshCoreUiState, onOpenConversation: (MeshCoreConversation) -> Unit) {
    if (state.conversations.isEmpty()) {
        MeshCoreEmptyState(icon = Res.drawable.ic_forum, title = stringResource(Res.string.meshcore_no_conversations))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(state.conversations, key = MeshCoreConversation::id) { conversation ->
            MeshCoreListRow(
                icon =
                if (conversation.isChannel) {
                    Res.drawable.ic_chat_bubble_outline
                } else {
                    Res.drawable.ic_person
                },
                title = conversation.displayTitle(),
                subtitle = conversation.lastMessage?.text ?: stringResource(Res.string.meshcore_setting_unavailable),
                modifier = Modifier.clickable { onOpenConversation(conversation) },
                trailingContent = {
                    Text(
                        text =
                        stringResource(
                            if (conversation.isChannel) {
                                Res.string.meshcore_channel_message
                            } else {
                                Res.string.meshcore_direct_message
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun MeshCoreEmptyState(icon: DrawableResource, title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.padding(18.dp))
            }
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun MeshCoreConversation.displayTitle(): String = if (isChannel) "#${title.removePrefix("#")}" else title

private fun MeshCoreSection.labelResource() = when (this) {
    MeshCoreSection.MESSAGES -> Res.string.meshcore_messages
    MeshCoreSection.CONTACTS_AND_CHANNELS -> Res.string.meshcore_contacts_channels
    MeshCoreSection.RADIO -> Res.string.meshcore_radio
}
