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

/** Serializes every local-node channel transaction, including built-in channel provisioning. */
class ChannelOperationLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
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
}
