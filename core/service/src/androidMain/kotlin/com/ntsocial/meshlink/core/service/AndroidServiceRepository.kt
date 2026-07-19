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
package com.ntsocial.meshlink.core.service

import com.ntsocial.meshlink.core.repository.ServiceRepository
import org.koin.core.annotation.Single

/**
 * Android-specific [ServiceRepository] that extends [ServiceRepositoryImpl] with AIDL service binding.
 *
 * The base class provides all reactive state management (connection state, error messages, mesh packets, etc.) in pure
 * KMP code. This subclass adds the [IMeshService] reference needed by [AndroidRadioControllerImpl] and the AIDL binder
 * in `MeshService`.
 */
@Single(binds = [ServiceRepository::class, AndroidServiceRepository::class])
@Suppress("DEPRECATION") // IMeshService is deprecated but still required for AIDL binding
class AndroidServiceRepository : ServiceRepositoryImpl() {
    var meshService: IMeshService? = null
        private set

    fun setMeshService(service: IMeshService?) {
        meshService = service
    }
}
