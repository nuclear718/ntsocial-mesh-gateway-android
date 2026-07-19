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

import android.net.nsd.NsdManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [ServiceDiscovery::class])
class AndroidServiceDiscovery(private val nsdManager: NsdManager) : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> =
        nsdManager.serviceList(NetworkConstants.SERVICE_TYPE).map { list ->
            list.map { info ->
                val txtMap = mutableMapOf<String, ByteArray>()
                info.attributes.forEach { (key, value) -> txtMap[key] = value }
                @Suppress("DEPRECATION")
                DiscoveredService(
                    name = info.serviceName,
                    hostAddress = info.host?.hostAddress ?: "",
                    port = info.port,
                    txt = txtMap,
                )
            }
        }
}
