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
package com.ntsocial.meshlink.core.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.service.ServiceAction
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.SharedContact

@KoinViewModel
class SharedContactViewModel(nodeRepository: NodeRepository, private val serviceRepository: ServiceRepository) :
    ViewModel() {

    val unfilteredNodes: StateFlow<List<Node>> =
        nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    fun addSharedContact(sharedContact: SharedContact) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.ImportContact(sharedContact)) }
}
