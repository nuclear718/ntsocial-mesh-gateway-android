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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Response envelope of `GET /resource/deviceLinks` on the Meshtastic API. The server resolves meshtastic/msh.to's
 * catalog into fully-classified links, so the client only stores and filters them.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class NetworkDeviceLinksResponse(
    val version: Int = 1,
    val generatedAt: String? = null,
    val source: String? = null,
    val links: List<NetworkDeviceLink> = emptyList(),
)

/**
 * A single resolved device link from the Meshtastic API.
 *
 * @param shortCode msh.to short code, e.g. `rokland-t-deck-plus`.
 * @param url the user-facing `https://msh.to/<shortCode>` link.
 * @param description human-readable label.
 * @param type authoritative classification: [TYPE_INTERNAL], [TYPE_VENDOR], or [TYPE_MARKETPLACE].
 * @param targets device `platformioTarget`s this link is attached to.
 * @param hwModels `hwModel` ints derived from [targets] server-side.
 * @param marketplace retailer key for marketplace links, else `null`.
 * @param regions ISO 3166-1 alpha-2 shipping regions; `null` = worldwide.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class NetworkDeviceLink(
    val shortCode: String = "",
    val url: String = "",
    val description: String? = null,
    val type: String = TYPE_INTERNAL,
    val targets: List<String>? = null,
    val hwModels: List<Int>? = null,
    val marketplace: String? = null,
    val regions: List<String>? = null,
) {
    companion object {
        const val TYPE_INTERNAL = "internal"
        const val TYPE_MARKETPLACE = "marketplace"
        const val TYPE_VENDOR = "vendor"
    }
}

/** Maps an API link to the cached domain model. Callers should drop [NetworkDeviceLink.TYPE_INTERNAL] links. */
fun NetworkDeviceLink.toDeviceLink(): DeviceLink = DeviceLink(
    shortCode = shortCode,
    description = description,
    isVendor = type == NetworkDeviceLink.TYPE_VENDOR,
    regions = regions,
    targets = targets,
)
