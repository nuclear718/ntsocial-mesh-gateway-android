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
package com.ntsocial.meshlink.core.ui.qr

import androidx.lifecycle.ViewModel
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_apply_failed_title
import com.ntsocial.meshlink.core.resources.channel_apply_invalid
import com.ntsocial.meshlink.core.resources.close
import com.ntsocial.meshlink.core.ui.component.ChannelApplyUiState
import com.ntsocial.meshlink.core.ui.component.showChannelApplyFailure
import com.ntsocial.meshlink.core.ui.component.toChannelApplyUiState
import com.ntsocial.meshlink.core.ui.util.AlertManager
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.ChannelSet

internal const val DEFAULT_MAX_CHANNELS = 8

@KoinViewModel
class ScannedQrCodeViewModel(
    private val radioConfigRepository: RadioConfigRepository,
    private val channelReliabilityManager: ChannelReliabilityManager,
    private val alertManager: AlertManager,
    nodeRepository: NodeRepository,
) : ViewModel() {

    private val _applyState = MutableStateFlow<ChannelApplyUiState>(ChannelApplyUiState.Idle)
    val applyState: StateFlow<ChannelApplyUiState> = _applyState.asStateFlow()
    private var dialogDismissed = false

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    val maxChannels =
        nodeRepository.myNodeInfo
            .map { it?.maxChannels?.takeIf { maximum -> maximum > 0 } ?: DEFAULT_MAX_CHANNELS }
            .stateInWhileSubscribed(
                initialValue = nodeRepository.myNodeInfo.value?.maxChannels?.takeIf { it > 0 } ?: DEFAULT_MAX_CHANNELS,
            )

    /** Applies the complete set and reports success only after a fresh matching radio readback. */
    fun setChannels(channelSet: ChannelSet): Job? {
        if (_applyState.value == ChannelApplyUiState.Applying) return null
        // Claim the request synchronously so the dialog can dismiss only after this ViewModel owns the work.
        _applyState.value = ChannelApplyUiState.Applying
        return safeLaunch(tag = "setChannels") {
            try {
                withContext(NonCancellable) {
                    val result = channelReliabilityManager.applyAndVerify(channelSet)
                    val state = result.toChannelApplyUiState()
                    _applyState.value = if (dialogDismissed) ChannelApplyUiState.Idle else state
                    when {
                        state is ChannelApplyUiState.Failed -> alertManager.showChannelApplyFailure(result)

                        state == ChannelApplyUiState.InvalidSettings ->
                            alertManager.showAlert(
                                titleRes = Res.string.channel_apply_failed_title,
                                messageRes = Res.string.channel_apply_invalid,
                                confirmTextRes = Res.string.close,
                            )

                        else -> Unit
                    }
                }
            } finally {
                // safeLaunch reports unexpected failures. An exception is not proof that the node rejected
                // the write or returned a mismatching readback, so keep it informational.
                if (_applyState.value == ChannelApplyUiState.Applying) {
                    _applyState.value =
                        if (dialogDismissed) ChannelApplyUiState.Idle else ChannelApplyUiState.WaitingForReconnect
                }
            }
        }
    }

    /** Marks an explicit preview presentation without clearing a result retained across radio reconnect. */
    fun onDialogShown() {
        // A new QR can arrive while a previously dismissed apply is still finishing. Do not attach that new
        // presentation to the old operation or let the old completion consume it.
        if (_applyState.value != ChannelApplyUiState.Applying) dialogDismissed = false
    }

    /** Explicit dismissal hides later non-terminal completion while allowing an admitted transaction to finish. */
    fun onDialogDismissed() {
        dialogDismissed = true
        if (_applyState.value != ChannelApplyUiState.Applying) clearApplyState()
    }

    fun clearApplyState() {
        _applyState.value = ChannelApplyUiState.Idle
    }
}
