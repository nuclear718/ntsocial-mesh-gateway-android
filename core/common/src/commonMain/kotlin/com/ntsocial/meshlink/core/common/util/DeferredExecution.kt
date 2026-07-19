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
package com.ntsocial.meshlink.core.common.util

import co.touchlab.kermit.Logger

/**
 * Sometimes when starting services we face situations where messages come in that require computation but we can't do
 * that computation yet because we are still waiting for some long running init to complete.
 *
 * This class lets you queue up closures to run at a later date and later on you can call run() to run all the
 * previously queued work.
 */
class DeferredExecution {
    private val queue = mutableListOf<() -> Unit>()

    /** Queues new work to be executed later. */
    fun add(fn: () -> Unit) {
        queue.add(fn)
    }

    /** Runs all work in the queue and clears it. */
    fun run() {
        Logger.d { "Running deferred execution, numJobs=${queue.size}" }
        queue.forEach { it() }
        queue.clear()
    }
}
