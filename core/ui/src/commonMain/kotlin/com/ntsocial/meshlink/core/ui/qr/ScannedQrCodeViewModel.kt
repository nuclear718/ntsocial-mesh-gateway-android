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
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.ui.util.getChannelList
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

internal const val DEFAULT_MAX_CHANNELS = 8

@KoinViewModel
class ScannedQrCodeViewModel(
    private val radioConfigRepository: RadioConfigRepository,
    private val radioController: RadioController,
    nodeRepository: NodeRepository,
) : ViewModel() {

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    val maxChannels =
        nodeRepository.myNodeInfo
            .map { it?.maxChannels?.takeIf { maximum -> maximum > 0 } ?: DEFAULT_MAX_CHANNELS }
            .stateInWhileSubscribed(
                initialValue = nodeRepository.myNodeInfo.value?.maxChannels?.takeIf { it > 0 } ?: DEFAULT_MAX_CHANNELS,
            )

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: ChannelSet) = safeLaunch(tag = "setChannels") {
        val currentSettings = radioConfigRepository.channelSetFlow.first().settings
        getChannelList(channelSet.settings, currentSettings).forEach { channel ->
            radioController.setLocalChannel(channel)
        }

        val loraConfig = channelSet.lora_config
        val currentLoraConfig = radioConfigRepository.localConfigFlow.first().lora
        if (loraConfig != null && currentLoraConfig != loraConfig) {
            radioController.setLocalConfig(Config(lora = loraConfig))
        }

        radioConfigRepository.replaceAllSettings(channelSet.settings)
    }
}
