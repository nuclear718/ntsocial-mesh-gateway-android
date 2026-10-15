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
package com.ntsocial.meshlink.feature.node.di

import com.ntsocial.meshlink.core.ui.viewmodel.EndpointViewModelFactory
import com.ntsocial.meshlink.feature.node.compass.CompassViewModel
import com.ntsocial.meshlink.feature.node.detail.NodeDetailViewModel
import com.ntsocial.meshlink.feature.node.list.NodeListViewModel
import com.ntsocial.meshlink.feature.node.metrics.MetricsViewModel

fun EndpointViewModelFactory.registerNodeEndpointViewModels() {
    register(NodeListViewModel::class) { state, _ ->
        NodeListViewModel(
            savedStateHandle = state,
            nodeRepository = get(),
            radioConfigRepository = get(),
            serviceRepository = get(),
            radioController = get(),
            radioInterfaceService = get(),
            nodeManagementActions = get(),
            getFilteredNodesUseCase = get(),
            nodeFilterPreferences = get(),
        )
    }
    register(NodeDetailViewModel::class) { state, _ ->
        NodeDetailViewModel(
            savedStateHandle = state,
            nodeManagementActions = get(),
            nodeRequestActions = get(),
            serviceRepository = get(),
            getNodeDetailsUseCase = get(),
            ensureRemoteAdminSession = get(),
            observeRemoteAdminSessionStatus = get(),
            snackbarManager = get(),
        )
    }
    register(MetricsViewModel::class) { _, parameters ->
        MetricsViewModel(
            destNum = parameters.get(),
            dispatchers = get(),
            meshLogRepository = get(),
            serviceRepository = get(),
            nodeRepository = get(),
            nodeRequestActions = get(),
            alertManager = get(),
            getNodeDetailsUseCase = get(),
            fileService = get(),
        )
    }
    register(CompassViewModel::class) { _, _ ->
        CompassViewModel(
            headingProvider = get(),
            phoneLocationProvider = get(),
            magneticFieldProvider = get(),
            dispatchers = get(),
        )
    }
}
