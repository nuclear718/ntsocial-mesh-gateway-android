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
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.ChannelSet

sealed interface ChannelQrApplyState {
    data object Idle : ChannelQrApplyState

    data object Applying : ChannelQrApplyState

    data object Verified : ChannelQrApplyState

    data class Failed(val result: ChannelReliabilityResult) : ChannelQrApplyState
}

internal const val DEFAULT_MAX_CHANNELS = 8

@KoinViewModel
class ScannedQrCodeViewModel(
    private val radioConfigRepository: RadioConfigRepository,
    private val channelReliabilityManager: ChannelReliabilityManager,
    nodeRepository: NodeRepository,
) : ViewModel() {

    private val _applyState = MutableStateFlow<ChannelQrApplyState>(ChannelQrApplyState.Idle)
    val applyState: StateFlow<ChannelQrApplyState> = _applyState.asStateFlow()

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    val maxChannels =
        nodeRepository.myNodeInfo
            .map { it?.maxChannels?.takeIf { maximum -> maximum > 0 } ?: DEFAULT_MAX_CHANNELS }
            .stateInWhileSubscribed(
                initialValue = nodeRepository.myNodeInfo.value?.maxChannels?.takeIf { it > 0 } ?: DEFAULT_MAX_CHANNELS,
            )

    /** Applies the complete set and reports success only after a fresh matching radio readback. */
    fun setChannels(channelSet: ChannelSet) = safeLaunch(tag = "setChannels") {
        if (_applyState.value == ChannelQrApplyState.Applying) return@safeLaunch
        _applyState.value = ChannelQrApplyState.Applying
        try {
            val result = channelReliabilityManager.applyAndVerify(channelSet)
            _applyState.value =
                if (result == ChannelReliabilityResult.VERIFIED) {
                    ChannelQrApplyState.Verified
                } else {
                    ChannelQrApplyState.Failed(result)
                }
        } finally {
            // safeLaunch reports unexpected failures, while this guard keeps the non-dismissible applying state
            // from
            // becoming permanent if persistence or another dependency throws before returning a typed result.
            if (_applyState.value == ChannelQrApplyState.Applying) {
                _applyState.value = ChannelQrApplyState.Failed(ChannelReliabilityResult.READBACK_FAILED)
            }
        }
    }

    fun clearApplyState() {
        _applyState.value = ChannelQrApplyState.Idle
    }
}
