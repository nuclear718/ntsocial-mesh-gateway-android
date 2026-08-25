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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ntsocial.meshlink.core.resources.channel_hub_purpose
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
import com.ntsocial.meshlink.core.resources.save
import com.ntsocial.meshlink.core.ui.icon.Channel
import com.ntsocial.meshlink.core.ui.icon.ChatBubbleOutline
import com.ntsocial.meshlink.core.ui.icon.Edit
import com.ntsocial.meshlink.core.ui.icon.Lock
import com.ntsocial.meshlink.core.ui.icon.LockOpen
import com.ntsocial.meshlink.core.ui.icon.MeshHub
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Muted
import org.jetbrains.compose.resources.stringResource

// Human-readable text in the Channel Hub uses the default sans-serif family for a modern, legible feel.
// Monospace is retained only as an intentional accent on technical identifiers (radio address suffix).
private val UiFont = FontFamily.Default
private val CodeFont = FontFamily.Monospace

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
                            Text(
                                stringResource(Res.string.channel_hub_title),
                                fontFamily = UiFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                            )
                            Text(
                                stringResource(Res.string.channel_hub_summary, state.groups.size, channelCount),
                                fontFamily = UiFont,
                                fontSize = 13.sp,
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
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = onSelectAll,
            text = {
                Text(
                    stringResource(Res.string.channel_hub_all),
                    fontFamily = UiFont,
                    fontSize = 14.sp,
                    fontWeight = if (selectedIndex == 0) FontWeight.SemiBold else FontWeight.Medium,
                )
            },
        )
        groups.forEachIndexed { index, group ->
            val palette = endpointPalette(group.appearance.accentToken)
            val isSelected = selectedIndex == index + 1
            Tab(
                selected = isSelected,
                onClick = { onSelectEndpoint(group.profile.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(palette.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${group.profile.displayName} · ${group.profile.addressSuffix.uppercase()}",
                            fontFamily = UiFont,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (group.unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            CountPill(
                                count = group.unreadCount,
                                container = palette.accent,
                                onContainer = palette.onAccent,
                            )
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier =
                Modifier.size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MeshtasticIcons.MeshHub,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(if (isLoading) Res.string.channel_hub_loading else Res.string.channel_hub_no_radios),
                fontFamily = UiFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isLoading) {
                Text(
                    stringResource(Res.string.channel_hub_no_radios_hint),
                    fontFamily = UiFont,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
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
        Modifier.fillMaxWidth()
            .border(width = 1.dp, color = palette.border, shape = RoundedCornerShape(26.dp))
            .semantics {
                heading()
                contentDescription =
                    "${group.profile.displayName}, ${group.profile.addressSuffix.uppercase()}, " + stateLabel
            },
        shape = RoundedCornerShape(26.dp),
        color = palette.container,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            EndpointCardHeader(group = group, palette = palette, onEditAppearance = onEditAppearance)
            when {
                group.channels.isNotEmpty() ->
                    group.channels.forEachIndexed { index, channel ->
                        FleetChannelRow(
                            channel = channel,
                            enabled = group.dataAvailable,
                            canSend = group.canSend,
                            palette = palette,
                            onClick = { onOpenChannel(channel) },
                        )
                        if (index != group.channels.lastIndex) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .padding(start = 74.dp, end = 16.dp)
                                    .height(1.dp)
                                    .background(palette.separator),
                            )
                        }
                    }

                !group.dataAvailable -> EndpointDataState(Res.string.channel_hub_loading)

                else -> EndpointDataState(Res.string.channel_hub_channels_empty)
            }
            OpenConversationsButton(palette = palette, onClick = onOpenConversations)
        }
    }
}

@Composable
private fun EndpointCardHeader(group: FleetChannelGroup, palette: EndpointPalette, onEditAppearance: () -> Unit) {
    val stateColor = endpointStateColor(group.sessionState)
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(palette.headerTint, palette.container)))
            .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EndpointAvatar(suffix = group.profile.addressSuffix.uppercase(), palette = palette, ringColor = stateColor)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
            Text(
                group.profile.displayName,
                fontFamily = UiFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
                fontFamily = UiFont,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                StatusChip(label = endpointStateLabel(group.sessionState), color = stateColor)
                if (!group.canSend && group.channels.isNotEmpty()) {
                    ReadOnlyChip()
                }
                if (group.unreadCount > 0) {
                    CountPill(count = group.unreadCount, container = palette.accent, onContainer = palette.onAccent)
                }
            }
        }
        IconButton(onClick = onEditAppearance) {
            Icon(
                MeshtasticIcons.Edit,
                contentDescription = stringResource(Res.string.channel_hub_edit_appearance),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EndpointAvatar(suffix: String, palette: EndpointPalette, ringColor: Color) {
    Box(
        modifier =
        Modifier.size(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ringColor.copy(alpha = 0.9f))
            .padding(2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(palette.gradientTop, palette.gradientBottom))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                suffix,
                fontFamily = CodeFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onAccent,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    // The label sits on a low-alpha tint of its own color, so deepen (light) / brighten (dark) it for legibility.
    val textColor = if (isAppliedSchemeDark()) lerp(color, Color.White, 0.22f) else lerp(color, Color.Black, 0.30f)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
        }
    }
}

@Composable
private fun ReadOnlyChip() {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)) {
        Text(
            stringResource(Res.string.channel_hub_cached_read_only),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontFamily = UiFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountPill(count: Int, container: Color, onContainer: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            count.toString(),
            modifier = Modifier.defaultMinSize(minWidth = 22.dp).padding(horizontal = 7.dp, vertical = 2.dp),
            fontFamily = UiFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = onContainer,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun FleetChannelRow(
    channel: FleetChannelSummary,
    enabled: Boolean,
    canSend: Boolean,
    palette: EndpointPalette,
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
            .defaultMinSize(minHeight = 72.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                role = Role.Button
                contentDescription = listOf(channel.name, availabilityLabel).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLeadingTile(channel = channel, palette = palette)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    fontFamily = UiFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(7.dp))
                ChannelSecurityIcon(channel.security)
                if (channel.isMuted) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        MeshtasticIcons.Muted,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            channel.lastMessageText?.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    preview,
                    fontFamily = UiFont,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            channel.lastMessageAtMillis?.let { timestamp ->
                Text(
                    DateFormatter.formatShortDate(timestamp),
                    fontFamily = UiFont,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (channel.unreadCount > 0) {
                CountPill(count = channel.unreadCount, container = palette.accent, onContainer = palette.onAccent)
            }
        }
    }
}

@Composable
private fun ChannelLeadingTile(channel: FleetChannelSummary, palette: EndpointPalette) {
    val isPrimary = channel.role == FleetChannelRole.PRIMARY
    Box(
        modifier =
        Modifier.size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (isPrimary) {
                    Brush.linearGradient(listOf(palette.gradientTop, palette.gradientBottom))
                } else {
                    Brush.linearGradient(listOf(palette.tileTint, palette.tileTint))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isPrimary) {
            Icon(
                MeshtasticIcons.Channel,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = palette.onAccent,
            )
        } else {
            Text(
                (channel.channelIndex ?: 0).toString(),
                fontFamily = UiFont,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentText,
            )
        }
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
private fun OpenConversationsButton(palette: EndpointPalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(onClick = onClick, shape = RoundedCornerShape(50), color = palette.tileTint) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    MeshtasticIcons.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = palette.accentText,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.channel_hub_open_conversations),
                    fontFamily = UiFont,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accentText,
                )
            }
        }
    }
}

@Composable
private fun EndpointDataState(resource: org.jetbrains.compose.resources.StringResource) {
    Text(
        stringResource(resource),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        fontFamily = UiFont,
        fontSize = 14.sp,
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
        title = {
            Text(
                stringResource(Res.string.channel_hub_appearance_title),
                fontFamily = UiFont,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(group.profile.displayName, fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it.take(48) },
                    label = { Text(stringResource(Res.string.channel_hub_purpose), fontFamily = UiFont) },
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
                                    .background(
                                        Brush.linearGradient(listOf(palette.gradientTop, palette.gradientBottom)),
                                    )
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
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
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
                Text(stringResource(Res.string.save), fontFamily = UiFont, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel), fontFamily = UiFont) }
        },
    )
}

private data class EndpointPalette(
    val accent: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val container: Color,
    val headerTint: Color,
    val tileTint: Color,
    val border: Color,
    val separator: Color,
    // Foreground that stays legible on top of the saturated accent (avatar/primary tile/unread pill).
    val onAccent: Color,
    // Readable accent used for text/icons that sit on the light accent tint (secondary tile, actions).
    val accentText: Color,
)

private fun accentColorFor(token: NodeAccentToken): Color = when (token) {
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

@Composable
private fun endpointPalette(token: NodeAccentToken): EndpointPalette {
    val accent = accentColorFor(token)
    val darkTheme = isAppliedSchemeDark()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Pick black-or-white by whichever has the higher WCAG contrast against the accent (crossover ~0.20 luminance),
    // so bright warm tokens (amber/lime/orange/cyan/teal) get dark glyphs instead of low-contrast white.
    val onAccent = if (accent.luminance() > 0.20f) Color(0xFF10151C) else Color.White
    return EndpointPalette(
        accent = accent,
        gradientTop = lerp(accent, Color.White, if (darkTheme) 0.05f else 0.08f),
        gradientBottom = lerp(accent, Color.Black, if (darkTheme) 0.30f else 0.22f),
        container = lerp(surface, accent, if (darkTheme) 0.10f else 0.05f),
        headerTint = lerp(surface, accent, if (darkTheme) 0.22f else 0.11f),
        tileTint = accent.copy(alpha = if (darkTheme) 0.24f else 0.14f),
        border = if (darkTheme) onSurface.copy(alpha = 0.14f) else accent.copy(alpha = 0.16f),
        separator = if (darkTheme) onSurface.copy(alpha = 0.12f) else accent.copy(alpha = 0.10f),
        onAccent = onAccent,
        accentText = if (darkTheme) lerp(accent, Color.White, 0.30f) else lerp(accent, Color.Black, 0.30f),
    )
}

/** Keys darkness off the actually-applied color scheme (not the system flag), so forced-theme users match. */
@Composable private fun isAppliedSchemeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
private fun endpointStateColor(state: EndpointSessionState): Color {
    val darkTheme = isAppliedSchemeDark()
    return when (state) {
        is EndpointSessionState.Ready -> if (darkTheme) Color(0xff22c55e) else Color(0xff16a34a)

        EndpointSessionState.Connecting,
        EndpointSessionState.Synchronizing,
        -> if (darkTheme) Color(0xff60a5fa) else Color(0xff2563eb)

        is EndpointSessionState.Degraded,
        is EndpointSessionState.Failed,
        -> if (darkTheme) Color(0xfffbbf24) else Color(0xffd97706)

        EndpointSessionState.Registered,
        EndpointSessionState.WaitingResource,
        -> MaterialTheme.colorScheme.outline
    }
}
