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
package com.ntsocial.meshlink.core.model.ntsocial

/**
 * Ephemeral readiness snapshot for the canonical NTsocial radio channel.
 *
 * This is deliberately transport-neutral so Android IPC adapters can expose it without leaking channel PSKs or radio
 * configuration. It is refreshed after node-database readiness and default-channel provisioning.
 */
data class NtsocialDefaultChannelStatus(
    val ready: Boolean = false,
    val channelIndex: Int? = null,
    val provisioningState: String = NOT_STARTED,
    val provisioningChannelChange: String? = null,
    val provisioningLoraApplied: Boolean? = null,
) {
    companion object {
        const val NOT_STARTED = "NOT_STARTED"
    }
}
