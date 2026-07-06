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
package com.ntsocial.meshlink.core.repository

import com.ntsocial.meshlink.core.common.util.currentRegionCode
import com.ntsocial.meshlink.core.model.DeviceLink
import kotlinx.coroutines.flow.Flow

/** Provides msh.to device links resolved by the Meshtastic API (`/resource/deviceLinks`) and cached locally. */
interface DeviceLinkRepository {
    /** Seeds the link table from the bundled snapshot if it is empty. */
    suspend fun ensureImported()

    /** Refreshes links from the API: upserts the resolved catalog and prunes short codes that no longer exist. */
    suspend fun reconcile()

    /** Links attached to a device's platformio target, region-filtered and sorted with vendor links first. */
    suspend fun getLinksForTarget(platformioTarget: String, regionCode: String = currentRegionCode()): List<DeviceLink>

    /** All cached links, sorted by short code. */
    fun observeAllLinks(): Flow<List<DeviceLink>>
}
