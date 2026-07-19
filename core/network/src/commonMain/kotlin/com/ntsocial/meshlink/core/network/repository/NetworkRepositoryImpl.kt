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
package com.ntsocial.meshlink.core.network.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single(binds = [NetworkRepository::class])
class NetworkRepositoryImpl(
    networkMonitor: NetworkMonitor,
    serviceDiscovery: ServiceDiscovery,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
) : NetworkRepository {

    override val networkAvailable: Flow<Boolean> by lazy {
        networkMonitor.networkAvailable
            .flowOn(dispatchers.io)
            .conflate()
            .shareIn(
                scope = processLifecycle.coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
            .distinctUntilChanged()
    }

    override val resolvedList: Flow<List<DiscoveredService>> by lazy {
        serviceDiscovery.resolvedServices
            .flowOn(dispatchers.io)
            .conflate()
            .shareIn(
                scope = processLifecycle.coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
    }
}
