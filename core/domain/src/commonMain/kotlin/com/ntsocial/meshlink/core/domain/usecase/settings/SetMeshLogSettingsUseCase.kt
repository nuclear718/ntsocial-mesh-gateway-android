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
package com.ntsocial.meshlink.core.domain.usecase.settings

import com.ntsocial.meshlink.core.repository.MeshLogPrefs
import com.ntsocial.meshlink.core.repository.MeshLogRepository
import org.koin.core.annotation.Single

/** Use case for managing mesh log settings. */
@Single
open class SetMeshLogSettingsUseCase
constructor(
    private val meshLogRepository: MeshLogRepository,
    private val meshLogPrefs: MeshLogPrefs,
) {
    /**
     * Sets the retention period for mesh logs.
     *
     * @param days The number of days to retain logs.
     */
    suspend fun setRetentionDays(days: Int) {
        val clamped = days.coerceIn(MeshLogPrefs.MIN_RETENTION_DAYS, MeshLogPrefs.MAX_RETENTION_DAYS)
        meshLogPrefs.setRetentionDays(clamped)
        meshLogRepository.deleteLogsOlderThan(clamped)
    }

    /**
     * Enables or disables mesh logging.
     *
     * @param enabled True to enable logging, false to disable.
     */
    suspend fun setLoggingEnabled(enabled: Boolean) {
        meshLogPrefs.setLoggingEnabled(enabled)
        if (!enabled) {
            meshLogRepository.deleteAll()
        } else {
            meshLogRepository.deleteLogsOlderThan(meshLogPrefs.retentionDays.value)
        }
    }
}
