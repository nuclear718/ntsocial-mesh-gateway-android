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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BootloaderOtaQuirk(
    /** Hardware model id, matches DeviceHardware.hwModel. */
    @SerialName("hwModel") val hwModel: Int,
    /** Optional slug for readability / tooling. */
    @SerialName("hwModelSlug") val hwModelSlug: String? = null,
    /**
     * Indicates that devices usually ship with a bootloader that does not support OTA out of the box and require a
     * one-time bootloader upgrade (typically via USB) before DFU updates from the app work.
     */
    @SerialName("requiresBootloaderUpgradeForOta") val requiresBootloaderUpgradeForOta: Boolean = false,
    /** Optional URL pointing to documentation on how to update the bootloader. */
    @SerialName("infoUrl") val infoUrl: String? = null,
)
