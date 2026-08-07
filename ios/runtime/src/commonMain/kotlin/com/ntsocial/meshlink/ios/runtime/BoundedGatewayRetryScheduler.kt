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
package com.ntsocial.meshlink.ios.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coalesces transient mailbox failures into one bounded, delayed retry series. */
internal class BoundedGatewayRetryScheduler(
    private val scope: CoroutineScope,
    private val maxAttempts: Int,
    private val delayMillisForAttempt: (Int) -> Long,
    private val retry: suspend () -> Boolean,
) {
    private var job: Job? = null

    init {
        require(maxAttempts > 0)
    }

    fun schedule() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                repeat(maxAttempts) { zeroBasedAttempt ->
                    val attempt = zeroBasedAttempt + 1
                    delay(delayMillisForAttempt(attempt).coerceAtLeast(1))
                    if (!retry()) return@launch
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
