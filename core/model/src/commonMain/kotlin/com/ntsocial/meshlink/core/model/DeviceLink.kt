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
package com.ntsocial.meshlink.core.model

import kotlinx.serialization.Serializable

/**
 * A msh.to device link resolved by the Meshtastic API (`/resource/deviceLinks`) and cached locally. Every link routes
 * through the msh.to redirect service.
 */
@Serializable
data class DeviceLink(
    val shortCode: String,
    val description: String? = null,
    val isVendor: Boolean = false,
    val regions: List<String>? = null,
    val targets: List<String>? = null,
) {
    /** The user-facing link, routed through the msh.to redirect service. */
    val url: String
        get() = "https://msh.to/$shortCode"
}
