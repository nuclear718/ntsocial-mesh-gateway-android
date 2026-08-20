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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelMutationLockTest {
    @Test
    fun `owner count stays nonzero while a queued owner becomes active`() = runTest {
        val lock = ChannelMutationLock()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        val first =
            async(start = CoroutineStart.UNDISPATCHED) {
                lock.withLock {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                }
            }
        firstEntered.await()
        assertEquals(1, lock.activeOrPendingOwners.value)

        val second =
            async(start = CoroutineStart.UNDISPATCHED) {
                lock.withLock {
                    secondEntered.complete(Unit)
                    releaseSecond.await()
                }
            }
        assertEquals(2, lock.activeOrPendingOwners.value)
        assertFalse(secondEntered.isCompleted)

        releaseFirst.complete(Unit)
        first.await()
        secondEntered.await()
        assertEquals(1, lock.activeOrPendingOwners.value)

        releaseSecond.complete(Unit)
        second.await()
        assertEquals(0, lock.activeOrPendingOwners.value)
    }

    @Test
    fun `stable channel admission holds a later mutation outside its critical section`() = runTest {
        val lock = ChannelMutationLock()
        val stableEntered = CompletableDeferred<Unit>()
        val releaseStable = CompletableDeferred<Unit>()
        val mutationEntered = CompletableDeferred<Unit>()

        val stable =
            async(start = CoroutineStart.UNDISPATCHED) {
                lock.tryWithStableChannels {
                    stableEntered.complete(Unit)
                    releaseStable.await()
                }
            }
        stableEntered.await()

        val mutation = async(start = CoroutineStart.UNDISPATCHED) { lock.withLock { mutationEntered.complete(Unit) } }
        assertEquals(1, lock.activeOrPendingOwners.value)
        assertFalse(mutationEntered.isCompleted)

        releaseStable.complete(Unit)
        assertTrue(stable.await())
        mutationEntered.await()
        mutation.await()
        assertEquals(0, lock.activeOrPendingOwners.value)
    }

    @Test
    fun `stable channel admission drops a sample while a mutation is active`() = runTest {
        val lock = ChannelMutationLock()
        val mutationEntered = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        var blockRan = false

        val mutation =
            async(start = CoroutineStart.UNDISPATCHED) {
                lock.withLock {
                    mutationEntered.complete(Unit)
                    releaseMutation.await()
                }
            }
        mutationEntered.await()

        assertFalse(lock.tryWithStableChannels { blockRan = true })
        assertFalse(blockRan)

        releaseMutation.complete(Unit)
        mutation.await()
        assertEquals(0, lock.activeOrPendingOwners.value)
    }
}
