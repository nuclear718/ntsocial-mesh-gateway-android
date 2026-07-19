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
package com.ntsocial.meshlink.feature.messaging

import androidx.lifecycle.ViewModel
import com.ntsocial.meshlink.core.common.util.ioDispatcher
import com.ntsocial.meshlink.core.database.entity.QuickChatAction
import com.ntsocial.meshlink.core.repository.QuickChatActionRepository
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class QuickChatViewModel(private val quickChatActionRepository: QuickChatActionRepository) : ViewModel() {
    val quickChatActions
        get() = quickChatActionRepository.getAllActions().stateInWhileSubscribed(initialValue = emptyList())

    fun updateActionPositions(actions: List<QuickChatAction>) {
        safeLaunch(context = ioDispatcher, tag = "updateActionPositions") {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }

    fun addQuickChatAction(action: QuickChatAction) =
        safeLaunch(context = ioDispatcher, tag = "addQuickChatAction") { quickChatActionRepository.upsert(action) }

    fun deleteQuickChatAction(action: QuickChatAction) =
        safeLaunch(context = ioDispatcher, tag = "deleteQuickChatAction") { quickChatActionRepository.delete(action) }
}
