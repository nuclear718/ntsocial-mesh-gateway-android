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
package com.ntsocial.meshlink.feature.node.list

import com.ntsocial.meshlink.core.model.NodeSortOption
import com.ntsocial.meshlink.core.repository.UiPrefs
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
open class NodeFilterPreferences constructor(private val uiPrefs: UiPrefs) {
    open val includeUnknown = uiPrefs.includeUnknown
    open val excludeInfrastructure = uiPrefs.excludeInfrastructure
    open val onlyOnline = uiPrefs.onlyOnline
    open val onlyDirect = uiPrefs.onlyDirect
    open val showIgnored = uiPrefs.showIgnored
    open val excludeMqtt = uiPrefs.excludeMqtt

    open val nodeSortOption =
        uiPrefs.nodeSort.map { NodeSortOption.entries.getOrElse(it) { NodeSortOption.VIA_FAVORITE } }

    open fun setNodeSort(option: NodeSortOption) {
        uiPrefs.setNodeSort(option.ordinal)
    }

    open fun toggleIncludeUnknown() {
        uiPrefs.setIncludeUnknown(!includeUnknown.value)
    }

    open fun toggleExcludeInfrastructure() {
        uiPrefs.setExcludeInfrastructure(!excludeInfrastructure.value)
    }

    open fun toggleOnlyOnline() {
        uiPrefs.setOnlyOnline(!onlyOnline.value)
    }

    open fun toggleOnlyDirect() {
        uiPrefs.setOnlyDirect(!onlyDirect.value)
    }

    open fun toggleShowIgnored() {
        uiPrefs.setShowIgnored(!showIgnored.value)
    }

    open fun toggleExcludeMqtt() {
        uiPrefs.setExcludeMqtt(!excludeMqtt.value)
    }
}
