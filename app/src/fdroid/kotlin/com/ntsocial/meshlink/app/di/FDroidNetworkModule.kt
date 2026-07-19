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
package com.ntsocial.meshlink.app.di

import com.ntsocial.meshlink.core.model.NetworkDeviceHardware
import com.ntsocial.meshlink.core.model.NetworkDeviceLinksResponse
import com.ntsocial.meshlink.core.model.NetworkFirmwareReleases
import com.ntsocial.meshlink.core.network.service.ApiService
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class FDroidNetworkModule {

    /**
     * F-Droid builds intentionally avoid network calls to the Meshtastic API.
     *
     * We throw [UnsupportedOperationException] (an [Exception], not an [Error]) so that `safeCatching {}` in the
     * repositories captures the failure and falls back to the bundled JSON assets instead of crashing the app.
     */
    @Single
    fun provideApiService(): ApiService = object : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> =
            throw UnsupportedOperationException("getDeviceHardware is not supported on F-Droid builds.")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse =
            throw UnsupportedOperationException("getDeviceLinks is not supported on F-Droid builds.")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases =
            throw UnsupportedOperationException("getFirmwareReleases is not supported on F-Droid builds.")
    }
}
