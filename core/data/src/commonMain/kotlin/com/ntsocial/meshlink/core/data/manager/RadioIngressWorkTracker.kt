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
package com.ntsocial.meshlink.core.data.manager

import com.ntsocial.meshlink.core.common.util.handledLaunch
import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

/**
 * Process-local barrier for work owned by one radio ingress generation.
 *
 * Selection pauses admission, rotates the generation, and waits without polling until every synchronous handler and
 * registered child job from the retired generation has completed. Work scheduled by a retired handler after pause is
 * rejected, so it cannot start against the replacement database.
 */
@Single
class RadioIngressWorkTracker {
    private val accepting = atomic(true)
    private val generation = atomic(1L)
    private val activeHandlers = atomic(0)
    private val jobs = atomic(persistentMapOf<Job, Long>())
    private val changeSignal = MutableStateFlow(0L)

    @Suppress("ReturnCount")
    fun enterHandler(): Long? {
        if (!accepting.value) return null
        val capturedGeneration = generation.value
        activeHandlers.incrementAndGet()
        if (!accepting.value || generation.value != capturedGeneration) {
            activeHandlers.decrementAndGet()
            signalChange()
            return null
        }
        return capturedGeneration
    }

    fun exitHandler() {
        activeHandlers.decrementAndGet()
        signalChange()
    }

    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        val capturedGeneration = generation.value
        if (!accepting.value) return CompletableDeferred(Unit)
        val job =
            scope.handledLaunch(start = CoroutineStart.LAZY) {
                if (generation.value == capturedGeneration && accepting.value) block()
            }
        register(job, capturedGeneration)
        job.start()
        return job
    }

    /**
     * Starts owned work immediately until its first suspension while still registering it before a selection barrier
     * may complete. This preserves the synchronous fast path used by the companion ingress cache.
     */
    @Suppress("ReturnCount")
    fun launchUndispatched(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        val capturedGeneration = generation.value
        if (!accepting.value) return CompletableDeferred(Unit)
        activeHandlers.incrementAndGet()
        if (!accepting.value || generation.value != capturedGeneration) {
            activeHandlers.decrementAndGet()
            signalChange()
            return CompletableDeferred(Unit)
        }
        val job =
            scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
                if (generation.value == capturedGeneration && accepting.value) block()
            }
        register(job, capturedGeneration)
        activeHandlers.decrementAndGet()
        signalChange()
        return job
    }

    /** Pauses new ingress and waits for all work admitted before the generation rotation. */
    suspend fun pauseAndAwaitRetiredWork() {
        accepting.value = false
        val retiredGeneration = generation.getAndIncrement()
        while (hasRetiredWork(retiredGeneration)) {
            val observed = changeSignal.value
            if (!hasRetiredWork(retiredGeneration)) break
            changeSignal.first { it != observed }
        }
    }

    fun resume() {
        accepting.value = true
        signalChange()
    }

    private fun register(job: Job, capturedGeneration: Long) {
        while (true) {
            val current = jobs.value
            if (jobs.compareAndSet(current, current.put(job, capturedGeneration))) break
        }
        job.invokeOnCompletion {
            while (true) {
                val current = jobs.value
                if (jobs.compareAndSet(current, current.remove(job))) break
            }
            signalChange()
        }
    }

    private fun hasRetiredWork(retiredGeneration: Long): Boolean =
        activeHandlers.value > 0 || jobs.value.values.any { it <= retiredGeneration }

    private fun signalChange() {
        changeSignal.update { it + 1 }
    }
}
