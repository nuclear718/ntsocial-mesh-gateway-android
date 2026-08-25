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
@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.ntsocial.meshlink.feature.messaging.fleet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.common.util.DateFormatter
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.conversation.ChannelSecurityKind
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearance
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelGroup
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelRole
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelSummary
import com.ntsocial.meshlink.core.radiofleet.conversation.NodeAccentToken
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.channel_hub_all
import com.ntsocial.meshlink.core.resources.channel_hub_appearance_title
import com.ntsocial.meshlink.core.resources.channel_hub_cached_read_only
import com.ntsocial.meshlink.core.resources.channel_hub_channels_empty
import com.ntsocial.meshlink.core.resources.channel_hub_edit_appearance
import com.ntsocial.meshlink.core.resources.channel_hub_loading
import com.ntsocial.meshlink.core.resources.channel_hub_no_radios
import com.ntsocial.meshlink.core.resources.channel_hub_no_radios_hint
import com.ntsocial.meshlink.core.resources.channel_hub_open_conversations
import com.ntsocial.meshlink.core.resources.channel_hub_primary
import com.ntsocial.meshlink.core.resources.channel_hub_purpose
import com.ntsocial.meshlink.core.resources.channel_hub_secondary
import com.ntsocial.meshlink.core.resources.channel_hub_security_clear
import com.ntsocial.meshlink.core.resources.channel_hub_security_custom
import com.ntsocial.meshlink.core.resources.channel_hub_security_well_known
import com.ntsocial.meshlink.core.resources.channel_hub_state_attention
import com.ntsocial.meshlink.core.resources.channel_hub_state_connected
import com.ntsocial.meshlink.core.resources.channel_hub_state_connecting
import com.ntsocial.meshlink.core.resources.channel_hub_state_offline
import com.ntsocial.meshlink.core.resources.channel_hub_state_synchronizing
import com.ntsocial.meshlink.core.resources.channel_hub_summary
import com.ntsocial.meshlink.core.resources.channel_hub_title
import com.ntsocial.meshlink.core.resources.channel_hub_unread
import com.ntsocial.meshlink.core.resources.save
import com.ntsocial.meshlink.core.ui.icon.ChatBubbleOutline
import com.ntsocial.meshlink.core.ui.icon.Edit
import com.ntsocial.meshlink.core.ui.icon.Lock
import com.ntsocial.meshlink.core.ui.icon.LockOpen
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Muted
import com.ntsocial.meshlink.core.ui.icon.RadioButtonUnchecked
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetChannelsScreen(
    state: FleetChannelsUiState,
    onSelectAll: () -> Unit,
    onSelectEndpoint: (RadioEndpointId) -> Unit,
    onOpenChannel: (FleetChannelGroup, FleetChannelSummary) -> Unit,
    onOpenConversations: (FleetChannelGroup) -> Unit,
    onUpdateAppearance: (RadioEndpointId, EndpointAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingGroup by remember { mutableStateOf<FleetChannelGroup?>(null) }
    val channelCount = state.groups.sumOf { it.channels.size }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(Res.string.channel_hub_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(Res.string.channel_hub_summary, state.groups.size, channelCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                if (state.groups.isNotEmpty()) {
                    FleetChannelTabs(
                        groups = state.groups,
                        selectedPage = state.selectedPage,
                        onSelectAll = onSelectAll,
                        onSelectEndpoint = onSelectEndpoint,
                    )
                }
            }
        },
    ) { innerPadding ->
        val stateHolder = rememberSaveableStateHolder()
        val pageKey =
            when (val page = state.selectedPage) {
                FleetChannelPage.All -> "all"
                is FleetChannelPage.Endpoint -> page.endpointId.value
            }
        stateHolder.SaveableStateProvider(pageKey) {
            FleetChannelList(
                groups = state.visibleGroups,
                isLoading = state.isLoading,
                onOpenChannel = onOpenChannel,
                onOpenConversations = onOpenConversations,
                onEditAppearance = { editingGroup = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    editingGroup?.let { group ->
        EndpointAppearanceDialog(
            group = group,
            onDismiss = { editingGroup = null },
            onSave = { appearance ->
                onUpdateAppearance(group.profile.id, appearance)
                editingGroup = null
            },
        )
    }
}

@Composable
private fun FleetChannelTabs(
    groups: List<FleetChannelGroup>,
    selectedPage: FleetChannelPage,
    onSelectAll: () -> Unit,
    onSelectEndpoint: (RadioEndpointId) -> Unit,
) {
    val selectedIndex =
        when (selectedPage) {
            FleetChannelPage.All -> 0

            is FleetChannelPage.Endpoint ->
                groups.indexOfFirst { it.profile.id == selectedPage.endpointId }.takeIf { it >= 0 }?.plus(1) ?: 0
        }
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = onSelectAll,
            text = { Text(stringResource(Res.string.channel_hub_all)) },
        )
        groups.forEachIndexed { index, group ->
            val palette = endpointPalette(group.appearance.accentToken)
            Tab(
                selected = selectedIndex == index + 1,
                onClick = { onSelectEndpoint(group.profile.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(palette.accent))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "${group.profile.displayName} · ${group.profile.addressSuffix.uppercase()}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (group.unreadCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Badge { Text(group.unreadCount.toString()) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FleetChannelList(
    groups: List<FleetChannelGroup>,
    isLoading: Boolean,
    onOpenChannel: (FleetChannelGroup, FleetChannelSummary) -> Unit,
    onOpenConversations: (FleetChannelGroup) -> Unit,
    onEditAppearance: (FleetChannelGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        EmptyFleetState(isLoading = isLoading, modifier = modifier)
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items = groups, key = { it.profile.id.value }, contentType = { "endpoint-channel-group" }) { group ->
            FleetEndpointCard(
                group = group,
                onOpenChannel = { onOpenChannel(group, it) },
                onOpenConversations = { onOpenConversations(group) },
                onEditAppearance = { onEditAppearance(group) },
            )
        }
    }
}

@Composable
private fun EmptyFleetState(isLoading: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = MeshtasticIcons.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(if (isLoading) Res.string.channel_hub_loading else Res.string.channel_hub_no_radios),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!isLoading) {
                Text(
                    stringResource(Res.string.channel_hub_no_radios_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FleetEndpointCard(
    group: FleetChannelGroup,
    onOpenChannel: (FleetChannelSummary) -> Unit,
    onOpenConversations: () -> Unit,
    onEditAppearance: () -> Unit,
) {
    val palette = endpointPalette(group.appearance.accentToken)
    val stateLabel = endpointStateLabel(group.sessionState)
    Surface(
        modifier =
        Modifier.fillMaxWidth().semantics {
            heading()
            contentDescription =
                "${group.profile.displayName}, ${group.profile.addressSuffix.uppercase()}, " + stateLabel
        },
        shape = RoundedCornerShape(22.dp),
        color = palette.container,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind { drawRect(color = palette.accent, size = size.copy(width = 5.dp.toPx())) }
                .padding(start = 5.dp),
        ) {
            EndpointCardHeader(group = group, palette = palette, onEditAppearance = onEditAppearance)
            HorizontalDivider(color = palette.accent.copy(alpha = 0.18f))
            when {
                group.channels.isNotEmpty() ->
                    group.channels.forEachIndexed { index, channel ->
                        FleetChannelRow(
                            channel = channel,
                            enabled = group.dataAvailable,
                            canSend = group.canSend,
                            accent = palette.accent,
                            onClick = { onOpenChannel(channel) },
                        )
                        if (index != group.channels.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 18.dp),
                                color = palette.accent.copy(alpha = 0.12f),
                            )
                        }
                    }

                !group.dataAvailable -> EndpointDataState(Res.string.channel_hub_loading)

                else -> EndpointDataState(Res.string.channel_hub_channels_empty)
            }
            HorizontalDivider(color = palette.accent.copy(alpha = 0.18f))
            TextButton(
                onClick = onOpenConversations,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
            ) {
                Icon(MeshtasticIcons.ChatBubbleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.channel_hub_open_conversations))
            }
        }
    }
}

@Composable
private fun EndpointCardHeader(group: FleetChannelGroup, palette: EndpointPalette, onEditAppearance: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                group.profile.addressSuffix.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.onAccent,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                group.profile.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val metadata =
                listOfNotNull(
                    "Meshtastic",
                    group.appearance.purposeLabel.takeIf(String::isNotBlank),
                    group.profile.addressSuffix.uppercase(),
                )
                    .joinToString(" · ")
            Text(
                metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val stateColor = endpointStateColor(group.sessionState)
                Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    endpointStateLabel(group.sessionState),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!group.canSend && group.channels.isNotEmpty()) {
                    Text(
                        " · ${stringResource(Res.string.channel_hub_cached_read_only)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (group.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(stringResource(Res.string.channel_hub_unread, group.unreadCount)) }
                }
            }
        }
        IconButton(onClick = onEditAppearance) {
            Icon(MeshtasticIcons.Edit, contentDescription = stringResource(Res.string.channel_hub_edit_appearance))
        }
    }
}

@Composable
private fun FleetChannelRow(
    channel: FleetChannelSummary,
    enabled: Boolean,
    canSend: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val availabilityLabel =
        if (canSend) {
            stringResource(Res.string.channel_hub_state_connected)
        } else {
            stringResource(Res.string.channel_hub_cached_read_only)
        }
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                role = Role.Button
                contentDescription = listOf(channel.name, availabilityLabel).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelRoleBadge(channel = channel, accent = accent)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(7.dp))
                ChannelSecurityIcon(channel.security)
                if (channel.isMuted) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        MeshtasticIcons.Muted,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            channel.lastMessageText?.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            channel.lastMessageAtMillis?.let { timestamp ->
                Text(
                    DateFormatter.formatShortDate(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (channel.unreadCount > 0) {
                Spacer(Modifier.height(5.dp))
                Badge(containerColor = accent) { Text(channel.unreadCount.toString()) }
            }
        }
    }
}

@Composable
private fun ChannelRoleBadge(channel: FleetChannelSummary, accent: Color) {
    val label =
        if (channel.role == FleetChannelRole.PRIMARY) {
            stringResource(Res.string.channel_hub_primary)
        } else {
            stringResource(Res.string.channel_hub_secondary, channel.channelIndex ?: 0)
        }
    Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.13f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChannelSecurityIcon(security: ChannelSecurityKind) {
    val description =
        when (security) {
            ChannelSecurityKind.CLEAR -> Res.string.channel_hub_security_clear
            ChannelSecurityKind.WELL_KNOWN -> Res.string.channel_hub_security_well_known
            ChannelSecurityKind.CUSTOM -> Res.string.channel_hub_security_custom
        }
    Icon(
        imageVector = if (security == ChannelSecurityKind.CLEAR) MeshtasticIcons.LockOpen else MeshtasticIcons.Lock,
        contentDescription = stringResource(description),
        modifier = Modifier.size(15.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EndpointDataState(resource: org.jetbrains.compose.resources.StringResource) {
    Text(
        stringResource(resource),
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EndpointAppearanceDialog(
    group: FleetChannelGroup,
    onDismiss: () -> Unit,
    onSave: (EndpointAppearance) -> Unit,
) {
    var selectedToken by remember(group.profile.id) { mutableStateOf(group.appearance.accentToken) }
    var purpose by remember(group.profile.id) { mutableStateOf(group.appearance.purposeLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.channel_hub_appearance_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(group.profile.displayName, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it.take(48) },
                    label = { Text(stringResource(Res.string.channel_hub_purpose)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NodeAccentToken.entries.chunked(4).forEach { rowTokens ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowTokens.forEach { token ->
                            val palette = endpointPalette(token)
                            Box(
                                modifier =
                                Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .border(
                                        width = if (token == selectedToken) 4.dp else 1.dp,
                                        color =
                                        if (token == selectedToken) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { selectedToken = token }
                                    .semantics {
                                        role = Role.RadioButton
                                        selected = token == selectedToken
                                        contentDescription = token.name.lowercase()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (token == selectedToken) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(palette.onAccent))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(group.appearance.copy(accentToken = selectedToken, purposeLabel = purpose.trim())) },
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private data class EndpointPalette(val container: Color, val accent: Color, val onAccent: Color)

@Composable
private fun endpointPalette(token: NodeAccentToken): EndpointPalette {
    val accent =
        when (token) {
            NodeAccentToken.INDIGO -> Color(0xff4f46e5)
            NodeAccentToken.EMERALD -> Color(0xff059669)
            NodeAccentToken.AMBER -> Color(0xffd97706)
            NodeAccentToken.CYAN -> Color(0xff0891b2)
            NodeAccentToken.BLUE -> Color(0xff2563eb)
            NodeAccentToken.VIOLET -> Color(0xff7c3aed)
            NodeAccentToken.ROSE -> Color(0xffe11d48)
            NodeAccentToken.LIME -> Color(0xff65a30d)
            NodeAccentToken.TEAL -> Color(0xff0d9488)
            NodeAccentToken.ORANGE -> Color(0xffea580c)
            NodeAccentToken.SLATE -> Color(0xff64748b)
            NodeAccentToken.FUCHSIA -> Color(0xffc026d3)
        }
    val darkTheme = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.25f
    val container = lerp(MaterialTheme.colorScheme.surfaceContainerLow, accent, if (darkTheme) 0.16f else 0.09f)
    return EndpointPalette(container = container, accent = accent, onAccent = Color.White)
}

@Composable
private fun endpointStateLabel(state: EndpointSessionState): String = stringResource(
    when (state) {
        is EndpointSessionState.Ready -> Res.string.channel_hub_state_connected

        EndpointSessionState.Connecting -> Res.string.channel_hub_state_connecting

        EndpointSessionState.Synchronizing -> Res.string.channel_hub_state_synchronizing

        EndpointSessionState.Registered,
        EndpointSessionState.WaitingResource,
        -> Res.string.channel_hub_state_offline

        is EndpointSessionState.Degraded,
        is EndpointSessionState.Failed,
        -> Res.string.channel_hub_state_attention
    },
)

@Composable
private fun endpointStateColor(state: EndpointSessionState): Color = when (state) {
    is EndpointSessionState.Ready -> Color(0xff16a34a)

    EndpointSessionState.Connecting,
    EndpointSessionState.Synchronizing,
    -> Color(0xff2563eb)

    is EndpointSessionState.Degraded,
    is EndpointSessionState.Failed,
    -> Color(0xffd97706)

    EndpointSessionState.Registered,
    EndpointSessionState.WaitingResource,
    -> MaterialTheme.colorScheme.outline
}
