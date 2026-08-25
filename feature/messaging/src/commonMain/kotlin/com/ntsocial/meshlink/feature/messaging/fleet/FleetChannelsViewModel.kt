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
package com.ntsocial.meshlink.feature.messaging.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearance
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearanceStore
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelGroup
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelsRepository
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

sealed interface FleetChannelPage {
    data object All : FleetChannelPage

    data class Endpoint(val endpointId: RadioEndpointId) : FleetChannelPage
}

data class FleetChannelsUiState(
    val groups: List<FleetChannelGroup> = emptyList(),
    val selectedPage: FleetChannelPage = FleetChannelPage.All,
    val isLoading: Boolean = true,
) {
    val visibleGroups: List<FleetChannelGroup>
        get() =
            when (val page = selectedPage) {
                FleetChannelPage.All -> groups.filter { it.appearance.showInAll }
                is FleetChannelPage.Endpoint -> groups.filter { it.profile.id == page.endpointId }
            }
}

@KoinViewModel
class FleetChannelsViewModel(
    repository: FleetChannelsRepository,
    private val appearanceStore: EndpointAppearanceStore,
) : ViewModel() {
    private val selectedPage = MutableStateFlow<FleetChannelPage>(FleetChannelPage.All)

    val uiState =
        combine(repository.groups, selectedPage) { groups, page ->
            FleetChannelsUiState(
                groups = groups,
                selectedPage = page.takeIf { it.isValidFor(groups) } ?: FleetChannelPage.All,
                isLoading = false,
            )
        }
            .stateInWhileSubscribed(FleetChannelsUiState())

    fun showAll() {
        selectedPage.value = FleetChannelPage.All
    }

    fun showEndpoint(endpointId: RadioEndpointId) {
        selectedPage.value = FleetChannelPage.Endpoint(endpointId)
    }

    fun updateAppearance(endpointId: RadioEndpointId, appearance: EndpointAppearance) {
        viewModelScope.launch { appearanceStore.update(endpointId, appearance) }
    }

    private fun FleetChannelPage.isValidFor(groups: List<FleetChannelGroup>): Boolean = this is FleetChannelPage.All ||
        (this is FleetChannelPage.Endpoint && groups.any { it.profile.id == endpointId })
}
