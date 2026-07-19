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
package com.ntsocial.meshlink.feature.node.domain.usecase

import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.NodeSortOption
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.feature.node.list.NodeFilterState
import com.ntsocial.meshlink.feature.node.model.isEffectivelyUnmessageable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.proto.Config

@Single
open class GetFilteredNodesUseCase constructor(private val nodeRepository: NodeRepository) {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    open operator fun invoke(filter: NodeFilterState, sort: NodeSortOption): Flow<List<Node>> = nodeRepository
        .getNodes(
            sort = sort,
            filter = filter.filterText,
            includeUnknown = filter.includeUnknown,
            onlyOnline = filter.onlyOnline,
            onlyDirect = filter.onlyDirect,
        )
        .map { list ->
            list
                .filter { node -> node.isIgnored == filter.showIgnored }
                .filter { node ->
                    if (filter.excludeInfrastructure) {
                        val role = node.user.role

                        @Suppress("DEPRECATION")
                        val infrastructureRoles =
                            listOf(
                                Config.DeviceConfig.Role.ROUTER,
                                Config.DeviceConfig.Role.REPEATER,
                                Config.DeviceConfig.Role.ROUTER_LATE,
                                Config.DeviceConfig.Role.CLIENT_BASE,
                            )
                        role !in infrastructureRoles && !node.isEffectivelyUnmessageable
                    } else {
                        true
                    }
                }
                .filter { node -> if (filter.excludeMqtt) !node.viaMqtt else true }
        }
}
