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
package com.ntsocial.meshlink.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.ntsocial.meshlink.core.model.DeviceLink
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "device_link")
data class DeviceLinkEntity(
    @PrimaryKey @ColumnInfo(name = "short_code") val shortCode: String,
    @ColumnInfo(name = "link_description") val description: String? = null,
    @ColumnInfo(name = "is_vendor") val isVendor: Boolean = false,
    val regions: List<String>? = null,
    val targets: List<String>? = null,
)

fun DeviceLink.asEntity() = DeviceLinkEntity(
    shortCode = shortCode,
    description = description,
    isVendor = isVendor,
    regions = regions,
    targets = targets,
)

fun DeviceLinkEntity.asExternalModel() = DeviceLink(
    shortCode = shortCode,
    description = description,
    isVendor = isVendor,
    regions = regions,
    targets = targets,
)
