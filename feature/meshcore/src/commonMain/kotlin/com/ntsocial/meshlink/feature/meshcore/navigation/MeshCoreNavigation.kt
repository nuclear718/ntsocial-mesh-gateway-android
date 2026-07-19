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
package com.ntsocial.meshlink.feature.meshcore.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.MeshCoreRoute
import com.ntsocial.meshlink.feature.meshcore.MeshCoreConversationScreen
import com.ntsocial.meshlink.feature.meshcore.MeshCoreScreen
import com.ntsocial.meshlink.feature.meshcore.MeshCoreViewModel
import org.koin.compose.viewmodel.koinViewModel

fun EntryProviderScope<NavKey>.meshCoreGraph(backStack: NavBackStack<NavKey>) {
    entry<MeshCoreRoute.MeshCoreGraph> {
        MeshCoreScreen(
            viewModel = koinViewModel<MeshCoreViewModel>(),
            onOpenConversation = { conversation ->
                backStack.add(
                    MeshCoreRoute.Conversation(
                        conversationId = conversation.id,
                        title = conversation.title,
                        isChannel = conversation.isChannel,
                    ),
                )
            },
        )
    }

    entry<MeshCoreRoute.Conversation> { route ->
        MeshCoreConversationScreen(
            viewModel = koinViewModel<MeshCoreViewModel>(),
            conversationId = route.conversationId,
            title = route.title,
            isChannel = route.isChannel,
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }
}
