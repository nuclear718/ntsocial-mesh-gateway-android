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
package com.ntsocial.meshlink.app.map

import androidx.lifecycle.SavedStateHandle
import com.ntsocial.meshlink.core.common.BuildConfigProvider
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.MapPrefs
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import com.ntsocial.meshlink.feature.map.BaseMapViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.LocalConfig

@Suppress("LongParameterList")
@KoinViewModel
class MapViewModel(
    mapPrefs: MapPrefs,
    packetRepository: PacketRepository,
    nodeRepository: NodeRepository,
    radioController: RadioController,
    radioConfigRepository: RadioConfigRepository,
    buildConfigProvider: BuildConfigProvider,
    savedStateHandle: SavedStateHandle,
) : BaseMapViewModel(mapPrefs, nodeRepository, packetRepository, radioController) {

    private val _selectedWaypointId = MutableStateFlow(savedStateHandle.get<Int>("waypointId"))
    val selectedWaypointId: StateFlow<Int?> = _selectedWaypointId.asStateFlow()

    fun setWaypointId(id: Int?) {
        if (_selectedWaypointId.value != id) {
            _selectedWaypointId.value = id
        }
    }

    var mapStyleId: Int
        get() = mapPrefs.mapStyle.value
        set(value) {
            mapPrefs.setMapStyle(value)
        }

    val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val config
        get() = localConfig.value

    val applicationId = buildConfigProvider.applicationId
}
