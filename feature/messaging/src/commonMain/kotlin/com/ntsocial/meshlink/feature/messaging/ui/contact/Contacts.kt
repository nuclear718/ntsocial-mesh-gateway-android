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
package com.ntsocial.meshlink.feature.messaging.ui.contact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.common.util.NumberFormatter
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.Contact
import com.ntsocial.meshlink.core.model.ContactSettings
import com.ntsocial.meshlink.core.model.util.TimeConstants
import com.ntsocial.meshlink.core.model.util.formatMuteRemainingTime
import com.ntsocial.meshlink.core.model.util.getChannel
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.are_you_sure
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.channel_invalid
import com.ntsocial.meshlink.core.resources.close_selection
import com.ntsocial.meshlink.core.resources.conversations
import com.ntsocial.meshlink.core.resources.currently
import com.ntsocial.meshlink.core.resources.delete
import com.ntsocial.meshlink.core.resources.delete_messages
import com.ntsocial.meshlink.core.resources.delete_selection
import com.ntsocial.meshlink.core.resources.mark_as_read
import com.ntsocial.meshlink.core.resources.mute_1_week
import com.ntsocial.meshlink.core.resources.mute_8_hours
import com.ntsocial.meshlink.core.resources.mute_always
import com.ntsocial.meshlink.core.resources.mute_notifications
import com.ntsocial.meshlink.core.resources.mute_status_always
import com.ntsocial.meshlink.core.resources.mute_status_muted_for_days
import com.ntsocial.meshlink.core.resources.mute_status_muted_for_hours
import com.ntsocial.meshlink.core.resources.mute_status_unmuted
import com.ntsocial.meshlink.core.resources.okay
import com.ntsocial.meshlink.core.resources.select_all
import com.ntsocial.meshlink.core.resources.unmute
import com.ntsocial.meshlink.core.ui.component.MainAppBar
import com.ntsocial.meshlink.core.ui.component.MeshtasticDialog
import com.ntsocial.meshlink.core.ui.component.MeshtasticImportFAB
import com.ntsocial.meshlink.core.ui.component.MeshtasticTextDialog
import com.ntsocial.meshlink.core.ui.component.ScrollToTopEvent
import com.ntsocial.meshlink.core.ui.component.smartScrollToTop
import com.ntsocial.meshlink.core.ui.icon.Close
import com.ntsocial.meshlink.core.ui.icon.Delete
import com.ntsocial.meshlink.core.ui.icon.MarkChatRead
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.SelectAll
import com.ntsocial.meshlink.core.ui.icon.VolumeMute
import com.ntsocial.meshlink.core.ui.icon.VolumeUp
import com.ntsocial.meshlink.core.ui.qr.ScannedQrCodeDialog
import com.ntsocial.meshlink.core.ui.util.rememberShowToastResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.SharedContact
import kotlin.time.Duration.Companion.days

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
fun ContactsScreen(
    onNavigateToShare: () -> Unit,
    sharedContactRequested: SharedContact?,
    requestChannelSet: ChannelSet?,
    onHandleDeepLink: (CommonUri, onInvalid: () -> Unit) -> Unit,
    onClearSharedContactRequested: () -> Unit,
    onClearRequestChannelUrl: () -> Unit,
    viewModel: ContactsViewModel,
    onClickNodeChip: (Int) -> Unit,
    onNavigateToMessages: (String) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    scrollToTopEvents: Flow<ScrollToTopEvent>?,
    activeContactKey: String?,
) {
    val showToast = rememberShowToastResource()
    val scope = rememberCoroutineScope()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var showMuteDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // State for managing selected contacts
    val selectedContactKeys = remember { mutableStateListOf<String>() }
    val isSelectionModeActive by remember { derivedStateOf { selectedContactKeys.isNotEmpty() } }

    // State for contacts list
    val pagedContacts = viewModel.contactListPaged.collectAsLazyPagingItems()

    // Create channel placeholders (always show broadcast contacts, even when empty)
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val channelPlaceholders =
        remember(channels.settings.size) {
            (0 until channels.settings.size).map { ch ->
                Contact(
                    contactKey = "$ch^all",
                    shortName = "$ch",
                    longName = channels.getChannel(ch)?.name ?: "Channel $ch",
                    lastMessageTime = null,
                    lastMessageText = "",
                    unreadCount = 0,
                    messageCount = 0,
                    isMuted = false,
                    isUnmessageable = false,
                    nodeColors = null,
                )
            }
        }

    val contactsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents?.collectLatest { event ->
            if (event is ScrollToTopEvent.ConversationsTabPressed) {
                contactsListState.smartScrollToTop(coroutineScope)
            }
        }
    }

    // Derived state for selected contacts and count
    val selectedContacts =
        remember(pagedContacts.itemCount, selectedContactKeys) {
            (0 until pagedContacts.itemCount)
                .mapNotNull { pagedContacts[it] }
                .filter { it.contactKey in selectedContactKeys }
        }
    // Get message count directly from repository for selected contacts
    var selectedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedContactKeys.size, selectedContactKeys.joinToString(",")) {
        selectedCount = viewModel.getTotalMessageCount(selectedContactKeys.toList())
    }
    val isAllMuted = remember(selectedContacts) { selectedContacts.all { it.isMuted } }

    requestChannelSet?.let { ScannedQrCodeDialog(it, onDismiss = { onClearRequestChannelUrl() }) }

    // Callback functions for item interaction
    val onContactClick: (Contact) -> Unit = { contact ->
        if (isSelectionModeActive) {
            // If in selection mode, toggle selection
            if (selectedContactKeys.contains(contact.contactKey)) {
                selectedContactKeys.remove(contact.contactKey)
            } else {
                selectedContactKeys.add(contact.contactKey)
            }
        } else {
            // If not in selection mode, navigate to messages
            onNavigateToMessages(contact.contactKey)
        }
    }

    val onNodeChipClick: (Contact) -> Unit = { contact ->
        if (contact.contactKey.contains("!")) {
            // if it's a node, look up the nodeNum including the !
            val nodeKey = contact.contactKey.substring(1)
            val node = viewModel.getNode(nodeKey)
            onNavigateToNodeDetails(node.num)
        } else {
            // Channels
        }
    }

    val onContactLongClick: (Contact) -> Unit = { contact ->
        // Enter selection mode and select the item on long press
        if (!isSelectionModeActive) {
            selectedContactKeys.add(contact.contactKey)
        } else {
            // If already in selection mode, toggle selection
            if (selectedContactKeys.contains(contact.contactKey)) {
                selectedContactKeys.remove(contact.contactKey)
            } else {
                selectedContactKeys.add(contact.contactKey)
            }
        }
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.conversations),
                ourNode = ourNode,
                showNodeChip = ourNode != null && connectionState is ConnectionState.Connected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {
                    val unreadCountTotal by viewModel.unreadCountTotal.collectAsStateWithLifecycle(0)
                    if (unreadCountTotal > 0) {
                        IconButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(
                                MeshtasticIcons.MarkChatRead,
                                contentDescription = stringResource(Res.string.mark_as_read),
                            )
                        }
                    }
                },
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
        floatingActionButton = {
            if (connectionState is ConnectionState.Connected) {
                MeshtasticImportFAB(
                    sharedContact = sharedContactRequested,
                    onImport = { uriString ->
                        onHandleDeepLink(CommonUri.parse(uriString)) {
                            scope.launch { showToast(Res.string.channel_invalid) }
                        }
                    },
                    onShareChannels = onNavigateToShare,
                    onDismissSharedContact = { onClearSharedContactRequested() },
                    isContactContext = true,
                )
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (isSelectionModeActive) {
                // Display selection toolbar when in selection mode
                SelectionToolbar(
                    selectedCount = selectedContactKeys.size,
                    onCloseSelection = { selectedContactKeys.clear() },
                    onMuteSelected = { showMuteDialog = true },
                    onDeleteSelected = { showDeleteDialog = true },
                    onSelectAll = {
                        selectedContactKeys.clear()
                        selectedContactKeys.addAll(
                            (0 until pagedContacts.itemCount).mapNotNull { pagedContacts[it]?.contactKey },
                        )
                    },
                    isAllMuted = isAllMuted, // Pass the derived state
                )
            }

            ContactListViewPaged(
                contacts = pagedContacts,
                channelPlaceholders = channelPlaceholders,
                selectedList = selectedContactKeys,
                activeContactKey = activeContactKey,
                onClick = onContactClick,
                onLongClick = onContactLongClick,
                onNodeChipClick = onNodeChipClick,
                listState = contactsListState,
                channels = channels,
            )
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            selectedCount = selectedCount,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteContacts(selectedContactKeys.toList())
                selectedContactKeys.clear()
            },
        )
    }

    // Get contact settings for the dialog
    val contactSettings by viewModel.getContactSettings().collectAsStateWithLifecycle(initialValue = emptyMap())

    if (showMuteDialog) {
        MuteNotificationsDialog(
            selectedContactKeys = selectedContactKeys.toList(),
            contactSettings = contactSettings,
            onDismiss = { showMuteDialog = false },
            onConfirm = { muteUntil ->
                showMuteDialog = false
                viewModel.setMuteUntil(selectedContactKeys.toList(), muteUntil)
                selectedContactKeys.clear()
            },
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun MuteNotificationsDialog(
    selectedContactKeys: List<String>,
    contactSettings: Map<String, ContactSettings>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit, // Lambda to handle the confirmed mute duration
) {
    // Options for mute duration
    val muteOptions = remember {
        listOf(
            Res.string.unmute to 0L,
            Res.string.mute_8_hours to TimeConstants.EIGHT_HOURS.inWholeMilliseconds,
            Res.string.mute_1_week to 7.days.inWholeMilliseconds,
            Res.string.mute_always to Long.MAX_VALUE,
        )
    }

    // State to hold the selected mute duration index
    var selectedOptionIndex by remember { mutableIntStateOf(2) } // Default to "Always"

    MeshtasticDialog(
        onDismiss = onDismiss, // Dismiss the dialog when clicked outside
        titleRes = Res.string.mute_notifications,
        confirmTextRes = Res.string.okay,
        onConfirm = {
            val selectedMuteDuration = muteOptions[selectedOptionIndex].second
            onConfirm(selectedMuteDuration)
            onDismiss() // Dismiss the dialog after confirming
        },
        dismissTextRes = Res.string.cancel,
        text = {
            Column {
                // Show current mute status
                selectedContactKeys.forEach { contactKey ->
                    contactSettings[contactKey]?.let { settings ->
                        val now = nowMillis
                        val statusText =
                            when {
                                settings.muteUntil > 0 && settings.muteUntil != Long.MAX_VALUE -> {
                                    val remaining = settings.muteUntil - now
                                    if (remaining > 0) {
                                        val (days, hours) = formatMuteRemainingTime(remaining)
                                        val hoursFormatted = NumberFormatter.format(hours, 1)
                                        if (days >= 1) {
                                            stringResource(Res.string.mute_status_muted_for_days, days, hoursFormatted)
                                        } else {
                                            stringResource(Res.string.mute_status_muted_for_hours, hoursFormatted)
                                        }
                                    } else {
                                        stringResource(Res.string.mute_status_unmuted)
                                    }
                                }

                                settings.muteUntil == Long.MAX_VALUE -> stringResource(Res.string.mute_status_always)

                                else -> stringResource(Res.string.mute_status_unmuted)
                            }
                        Text(
                            text = stringResource(Res.string.currently) + " " + statusText,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }

                muteOptions.forEachIndexed { index, (stringRes, _) ->
                    val isSelected = index == selectedOptionIndex
                    val text = stringResource(stringRes)
                    Row(
                        modifier =
                        Modifier.fillMaxWidth()
                            .selectable(selected = isSelected, onClick = { selectedOptionIndex = index })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = { selectedOptionIndex = index })
                        Text(text = text, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    selectedCount: Int, // Number of items to be deleted
    onDismiss: () -> Unit,
    onConfirm: () -> Unit, // Lambda to handle the delete action
) {
    val deleteMessage =
        pluralStringResource(
            Res.plurals.delete_messages,
            selectedCount,
            selectedCount, // Pass the count as a format argument
        )

    MeshtasticTextDialog(
        titleRes = Res.string.are_you_sure,
        message = deleteMessage,
        confirmTextRes = Res.string.delete,
        onConfirm = {
            onConfirm()
            onDismiss() // Dismiss the dialog after confirming
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionToolbar(
    selectedCount: Int,
    onCloseSelection: () -> Unit,
    onMuteSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    isAllMuted: Boolean,
) {
    TopAppBar(
        title = { Text(text = "$selectedCount") },
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.close_selection))
            }
        },
        actions = {
            IconButton(onClick = onMuteSelected) {
                Icon(
                    imageVector =
                    if (isAllMuted) {
                        MeshtasticIcons.VolumeUp
                    } else {
                        MeshtasticIcons.VolumeMute
                    },
                    contentDescription =
                    if (isAllMuted) {
                        "Unmute selected"
                    } else {
                        "Mute selected"
                    },
                )
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete_selection))
            }
            IconButton(onClick = onSelectAll) {
                Icon(MeshtasticIcons.SelectAll, contentDescription = stringResource(Res.string.select_all))
            }
        },
    )
}

@Composable
private fun ContactListViewPaged(
    contacts: LazyPagingItems<Contact>,
    channelPlaceholders: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    channels: ChannelSet? = null,
) {
    val haptic = LocalHapticFeedback.current
    Box(modifier = modifier.fillMaxSize()) {
        if (contacts.loadState.refresh is LoadState.Loading && contacts.itemCount == 0) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            ContactListContentInternal(
                contacts = contacts,
                channelPlaceholders = channelPlaceholders,
                selectedList = selectedList,
                activeContactKey = activeContactKey,
                onClick = onClick,
                onLongClick = onLongClick,
                onNodeChipClick = onNodeChipClick,
                listState = listState,
                channels = channels,
                haptic = haptic,
            )
        }
    }
}

@Composable
private fun ContactListContentInternal(
    contacts: LazyPagingItems<Contact>,
    channelPlaceholders: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    listState: LazyListState,
    channels: ChannelSet?,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val visiblePlaceholders = rememberVisiblePlaceholders(contacts, channelPlaceholders)

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        contactListPagedItems(
            contacts = contacts,
            selectedList = selectedList,
            activeContactKey = activeContactKey,
            onClick = onClick,
            onLongClick = onLongClick,
            onNodeChipClick = onNodeChipClick,
            channels = channels,
            haptic = haptic,
        )

        contactListPlaceholdersItems(
            placeholders = visiblePlaceholders,
            selectedList = selectedList,
            activeContactKey = activeContactKey,
            onClick = onClick,
            onLongClick = onLongClick,
            onNodeChipClick = onNodeChipClick,
            channels = channels,
            haptic = haptic,
        )

        contactListAppendLoadingItem(contacts)
    }
}

private fun LazyListScope.contactListPlaceholdersItems(
    placeholders: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    channels: ChannelSet?,
    haptic: HapticFeedback,
) {
    items(count = placeholders.size, key = { index -> "${placeholders[index].contactKey}_placeholder" }) { index ->
        val contact = placeholders[index]
        ContactItem(
            contact = contact,
            selected = selectedList.contains(contact.contactKey),
            isActive = contact.contactKey == activeContactKey,
            onClick = { onClick(contact) },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick(contact)
            },
            onNodeChipClick = { onNodeChipClick(contact) },
            channels = channels,
        )
    }
}

private fun LazyListScope.contactListPagedItems(
    contacts: LazyPagingItems<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    channels: ChannelSet?,
    haptic: HapticFeedback,
) {
    items(count = contacts.itemCount, key = contacts.itemKey { it.contactKey }) { index ->
        contacts[index]?.let { contact ->
            ContactItem(
                contact = contact,
                selected = selectedList.contains(contact.contactKey),
                isActive = contact.contactKey == activeContactKey,
                onClick = { onClick(contact) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(contact)
                },
                onNodeChipClick = { onNodeChipClick(contact) },
                channels = channels,
            )
        }
    }
}

private fun LazyListScope.contactListAppendLoadingItem(contacts: LazyPagingItems<Contact>) {
    if (contacts.loadState.append is LoadState.Loading) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun rememberVisiblePlaceholders(
    contacts: LazyPagingItems<Contact>,
    channelPlaceholders: List<Contact>,
): List<Contact> = remember(contacts.itemCount, channelPlaceholders) {
    val pagedKeys = (0 until contacts.itemCount).mapNotNull { contacts[it]?.contactKey }.toSet()
    channelPlaceholders.filter { it.contactKey !in pagedKeys }
}
