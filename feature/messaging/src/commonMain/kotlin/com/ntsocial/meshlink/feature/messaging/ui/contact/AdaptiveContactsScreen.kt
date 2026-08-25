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

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.navigation.ChannelsRoute
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.ui.component.ScrollToTopEvent
import kotlinx.coroutines.flow.Flow
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.SharedContact

@Composable
@Suppress("ViewModelForwarding") // Transitional Navigation 3 wrapper; ContactsScreen still owns its presentation state.
fun AdaptiveContactsScreen(
    backStack: NavBackStack<NavKey>,
    contactsViewModel: ContactsViewModel,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    sharedContactRequested: SharedContact?,
    requestChannelSet: ChannelSet?,
    onHandleDeepLink: (CommonUri, onInvalid: () -> Unit) -> Unit,
    onClearSharedContactRequested: () -> Unit,
    onClearRequestChannelUrl: () -> Unit,
    endpointId: String? = null,
    expectedGeneration: Long = 0L,
) {
    ContactsScreen(
        onNavigateToShare = { backStack.add(ChannelsRoute.ChannelsGraph) },
        sharedContactRequested = sharedContactRequested,
        requestChannelSet = requestChannelSet,
        onHandleDeepLink = onHandleDeepLink,
        onClearSharedContactRequested = onClearSharedContactRequested,
        onClearRequestChannelUrl = onClearRequestChannelUrl,
        viewModel = contactsViewModel,
        onClickNodeChip = { backStack.add(NodesRoute.NodeDetail(it)) },
        onNavigateToMessages = { contactKey ->
            backStack.add(
                endpointId?.let {
                    ContactsRoute.FleetMessages(
                        endpointId = it,
                        contactKey = contactKey,
                        expectedGeneration = expectedGeneration,
                    )
                } ?: ContactsRoute.Messages(contactKey),
            )
        },
        onNavigateToNodeDetails = { backStack.add(NodesRoute.NodeDetail(it)) },
        scrollToTopEvents = scrollToTopEvents,
        activeContactKey = null,
    )
}
