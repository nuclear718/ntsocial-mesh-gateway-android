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
package com.ntsocial.meshlink.core.network.service

import com.ntsocial.meshlink.core.model.NetworkDeviceHardware
import com.ntsocial.meshlink.core.model.NetworkDeviceLinksResponse
import com.ntsocial.meshlink.core.model.NetworkFirmwareReleases
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.koin.core.annotation.Single

/** Client for the Meshtastic public API (device hardware catalog and firmware releases). */
interface ApiService {
    /** Fetches the device hardware catalog from the Meshtastic API. */
    suspend fun getDeviceHardware(): List<NetworkDeviceHardware>

    /** Fetches the resolved msh.to device links catalog from the Meshtastic API. */
    suspend fun getDeviceLinks(): NetworkDeviceLinksResponse

    /** Fetches the list of available firmware releases from the Meshtastic API. */
    suspend fun getFirmwareReleases(): NetworkFirmwareReleases
}

/**
 * Ktor-based [ApiService] implementation.
 *
 * Uses relative paths — the base URL is set via the `DefaultRequest` plugin in the platform Koin modules.
 *
 * Registered with `binds = []` to prevent Koin from auto-binding to [ApiService]; host modules (`app`, `desktop`)
 * provide their own explicit `ApiService` binding to allow platform-specific `HttpClient` engines.
 */
@Single(binds = [])
class ApiServiceImpl(private val client: HttpClient) : ApiService {
    override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = client.get("resource/deviceHardware").body()

    override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = client.get("resource/deviceLinks").body()

    override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = client.get("github/firmware/list").body()
}
