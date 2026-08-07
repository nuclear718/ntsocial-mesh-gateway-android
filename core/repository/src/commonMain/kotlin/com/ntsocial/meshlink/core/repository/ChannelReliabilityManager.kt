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
@file:Suppress("FunctionLiteral", "FunctionSignature", "MaxLineLength")

package com.ntsocial.meshlink.core.repository

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.proto.ChannelSet

/** Outcome of a verified channel write or an explicit snapshot-protection action. */
enum class ChannelReliabilityResult {
    VERIFIED,
    PROTECTED,
    PROTECTION_DISABLED,
    REPAIRED,
    NO_REPAIR_NEEDED,
    NO_SNAPSHOT,
    CONFLICT,
    DISCONNECTED,
    IDENTITY_UNAVAILABLE,
    INVALID_CHANNEL_SET,
    SESSION_UNAVAILABLE,
    RADIO_REJECTED,
    READBACK_FAILED,
}

/** Serializes radio-selection, exact command admission, handshake commit, and Gateway route/admission boundaries. */
class ChannelOperationLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

/**
 * Serializes complete channel mutations without blocking the handshake commit that produces their verified readback. A
 * lease is valid only for the dynamic extent of [withLock] and lets one owner compose repair, provisioning, and final
 * activation without pretending that [kotlinx.coroutines.sync.Mutex] is reentrant.
 */
class ChannelMutationLock {
    class Lease internal constructor(internal val owner: ChannelMutationLock) {
        internal var active: Boolean = true
    }

    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend (Lease) -> T): T = mutex.withLock {
        val lease = Lease(this)
        try {
            block(lease)
        } finally {
            lease.active = false
        }
    }

    suspend fun <T> withLease(lease: Lease?, block: suspend (Lease) -> T): T = if (lease == null) {
        withLock(block)
    } else {
        require(lease.owner === this && lease.active) {
            "Channel mutation lease is stale or belongs to another lock"
        }
        block(lease)
    }
}

/** Applies local channel sets with radio acknowledgement/readback and owns opt-in snapshot protection. */
interface ChannelReliabilityManager {
    /** Whether the currently connected stable radio identity has an explicit protected snapshot. */
    val isProtected: StateFlow<Boolean>

    /** Replaces the local radio's channels and reports success only after a matching full readback. */
    suspend fun applyAndVerify(channelSet: ChannelSet): ChannelReliabilityResult

    /** Saves the latest complete readback as the user-approved snapshot for this radio. */
    suspend fun protectCurrentChannelSet(): ChannelReliabilityResult

    /** Stops automatic protection for the currently connected radio. */
    suspend fun disableProtection(): ChannelReliabilityResult

    /** Conservatively repairs only secondary slots proven missing from a protected snapshot. */
    suspend fun reconcileProtectedChannelSet(): ChannelReliabilityResult

    /**
     * Reconciles only if [expectedRadioSessionEpoch] is still the configured active radio after acquiring the shared
     * channel-operation lock. This prevents delayed handshake work from repairing a replacement radio.
     */
    suspend fun reconcileProtectedChannelSetForSession(expectedRadioSessionEpoch: Long): ChannelReliabilityResult =
        reconcileProtectedChannelSet()

    /** Same exact-session reconciliation composed inside an already validated channel-mutation lease. */
    suspend fun reconcileProtectedChannelSetForSession(
        expectedRadioSessionEpoch: Long,
        mutationLease: ChannelMutationLock.Lease,
    ): ChannelReliabilityResult = reconcileProtectedChannelSetForSession(expectedRadioSessionEpoch)
}
