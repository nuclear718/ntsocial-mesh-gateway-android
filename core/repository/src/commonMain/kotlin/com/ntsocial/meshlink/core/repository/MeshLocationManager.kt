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
package com.ntsocial.meshlink.core.repository

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.proto.Position

/** Interface for managing the local node's location updates and reporting. */
interface MeshLocationManager {
    /**
     * Records the desired location callback and starts updates when platform location access is currently allowed.
     *
     * Android's foreground-service gate can become legal after this call (for example, after a permission grant), so
     * implementations must retain the callback for [restart].
     */
    fun start(scope: CoroutineScope, sendPositionFn: (Position) -> Unit)

    /** Retries a previously requested start after permission or foreground-service conditions change. */
    fun restart()

    /**
     * Enables or suspends platform location access without changing the user's desired sharing state.
     *
     * Android uses this to ensure location updates run only while the service legally holds location access. Other
     * platforms may implement it as a no-op.
     */
    fun setLocationAccessAllowed(allowed: Boolean)

    /** Stops location updates. */
    fun stop()
}
