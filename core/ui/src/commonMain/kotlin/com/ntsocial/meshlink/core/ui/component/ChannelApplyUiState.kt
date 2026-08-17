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
package com.ntsocial.meshlink.core.ui.component

import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_apply_failed
import com.ntsocial.meshlink.core.resources.channel_apply_failed_title
import com.ntsocial.meshlink.core.resources.channel_apply_rejected
import com.ntsocial.meshlink.core.resources.close
import com.ntsocial.meshlink.core.ui.util.AlertManager
import org.jetbrains.compose.resources.StringResource

/** Non-blocking UI state for a local channel mutation. */
sealed interface ChannelApplyUiState {
    data object Idle : ChannelApplyUiState

    data object Applying : ChannelApplyUiState

    data object WaitingForReconnect : ChannelApplyUiState

    data object InvalidSettings : ChannelApplyUiState

    data object Verified : ChannelApplyUiState

    data class Failed(val result: ChannelReliabilityResult) : ChannelApplyUiState
}

/** Keeps recoverable no-response/session outcomes informational while preserving terminal failures. */
fun ChannelReliabilityResult.toChannelApplyUiState(): ChannelApplyUiState = when (this) {
    ChannelReliabilityResult.VERIFIED -> ChannelApplyUiState.Verified

    ChannelReliabilityResult.VERIFICATION_PENDING,
    ChannelReliabilityResult.DISCONNECTED,
    ChannelReliabilityResult.IDENTITY_UNAVAILABLE,
    ChannelReliabilityResult.SESSION_UNAVAILABLE,
    -> ChannelApplyUiState.WaitingForReconnect

    ChannelReliabilityResult.INVALID_CHANNEL_SET -> ChannelApplyUiState.InvalidSettings

    ChannelReliabilityResult.RADIO_REJECTED,
    ChannelReliabilityResult.READBACK_FAILED,
    -> ChannelApplyUiState.Failed(this)

    // These results are not produced by applyAndVerify. Keep future/impossible outcomes
    // informational instead of manufacturing a node failure popup.
    else -> ChannelApplyUiState.WaitingForReconnect
}

/** Uses the app-wide alert host only for a typed terminal channel-apply failure. */
fun AlertManager.showChannelApplyFailure(result: ChannelReliabilityResult) {
    if (result != ChannelReliabilityResult.RADIO_REJECTED && result != ChannelReliabilityResult.READBACK_FAILED) {
        return
    }
    showAlert(
        titleRes = Res.string.channel_apply_failed_title,
        messageRes = result.channelApplyFailureMessage(),
        confirmTextRes = Res.string.close,
    )
}

private fun ChannelReliabilityResult.channelApplyFailureMessage(): StringResource = when (this) {
    ChannelReliabilityResult.RADIO_REJECTED -> Res.string.channel_apply_rejected
    else -> Res.string.channel_apply_failed
}
