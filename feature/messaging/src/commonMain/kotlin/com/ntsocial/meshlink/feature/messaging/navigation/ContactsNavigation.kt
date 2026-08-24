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
package com.ntsocial.meshlink.feature.messaging.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.navigation.replaceLast
import com.ntsocial.meshlink.core.ui.component.ScrollToTopEvent
import com.ntsocial.meshlink.core.ui.viewmodel.scopedViewModel
import com.ntsocial.meshlink.feature.messaging.QuickChatScreen
import com.ntsocial.meshlink.feature.messaging.QuickChatViewModel
import com.ntsocial.meshlink.feature.messaging.ui.contact.AdaptiveContactsScreen
import com.ntsocial.meshlink.feature.messaging.ui.contact.ContactsViewModel
import com.ntsocial.meshlink.feature.messaging.ui.sharing.ShareScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.contactsGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent> = MutableSharedFlow(),
) {
    entry<ContactsRoute.ContactsGraph>(metadata = { ListDetailSceneStrategy.listPane() }) {
        ContactsEntryContent(backStack = backStack, scrollToTopEvents = scrollToTopEvents)
    }

    entry<ContactsRoute.Contacts>(metadata = { ListDetailSceneStrategy.listPane() }) {
        ContactsEntryContent(backStack = backStack, scrollToTopEvents = scrollToTopEvents)
    }

    entry<ContactsRoute.Messages>(metadata = { ListDetailSceneStrategy.detailPane() }) { args ->
        val contactKey = args.contactKey
        val messageViewModel: com.ntsocial.meshlink.feature.messaging.MessageViewModel =
            scopedViewModel(key = "messages-$contactKey")
        messageViewModel.setContactKey(contactKey)

        com.ntsocial.meshlink.feature.messaging.MessageScreen(
            contactKey = contactKey,
            message = args.message,
            viewModel = messageViewModel,
            navigateToNodeDetails = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            navigateToQuickChatOptions =
            dropUnlessResumed { backStack.add(com.ntsocial.meshlink.core.navigation.ContactsRoute.QuickChat) },
            navigateToFilterSettings = dropUnlessResumed { backStack.add(SettingsRoute.FilterSettings) },
            onNavigateBack = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoute.Share>(metadata = { ListDetailSceneStrategy.extraPane() }) { args ->
        val message = args.message
        val viewModel = scopedViewModel<ContactsViewModel>()
        ShareScreen(
            viewModel = viewModel,
            onConfirm = { contactKey -> backStack.replaceLast(ContactsRoute.Messages(contactKey, message)) },
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoute.QuickChat>(metadata = { ListDetailSceneStrategy.extraPane() }) {
        val viewModel = scopedViewModel<QuickChatViewModel>()
        QuickChatScreen(viewModel = viewModel, onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }
}

@Composable
fun ContactsEntryContent(backStack: NavBackStack<NavKey>, scrollToTopEvents: Flow<ScrollToTopEvent>) {
    val uiViewModel: com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel = koinViewModel()
    val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()
    val contactsViewModel = scopedViewModel<ContactsViewModel>()

    AdaptiveContactsScreen(
        backStack = backStack,
        contactsViewModel = contactsViewModel,
        scrollToTopEvents = scrollToTopEvents,
        sharedContactRequested = sharedContactRequested,
        requestChannelSet = requestChannelSet,
        onHandleDeepLink = uiViewModel::handleDeepLink,
        onClearSharedContactRequested = uiViewModel::clearSharedContactRequested,
        onClearRequestChannelUrl = uiViewModel::clearRequestChannelUrl,
    )
}
