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
package com.ntsocial.meshlink.feature.settings.channel

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.util.toChannelSet
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.DataPair
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.ui.component.ChannelApplyUiState
import com.ntsocial.meshlink.core.ui.component.showChannelApplyFailure
import com.ntsocial.meshlink.core.ui.component.toChannelApplyUiState
import com.ntsocial.meshlink.core.ui.util.AlertManager
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig

@KoinViewModel
class ChannelViewModel(
    private val radioController: RadioController,
    private val radioConfigRepository: RadioConfigRepository,
    private val analytics: PlatformAnalytics,
    private val channelReliabilityManager: ChannelReliabilityManager,
    private val alertManager: AlertManager,
) : ViewModel() {

    val connectionState = radioController.connectionState

    val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    val isChannelProtectionEnabled = channelReliabilityManager.isProtected

    private val _channelOperationResult = MutableStateFlow<ChannelReliabilityResult?>(null)
    val channelOperationResult: StateFlow<ChannelReliabilityResult?> = _channelOperationResult.asStateFlow()

    private val _channelApplyState = MutableStateFlow<ChannelApplyUiState>(ChannelApplyUiState.Idle)
    val channelApplyState: StateFlow<ChannelApplyUiState> = _channelApplyState.asStateFlow()

    // managed mode disables all access to configuration
    val isManaged: Boolean
        get() = localConfig.value.security?.is_managed == true

    var txEnabled: Boolean
        get() = localConfig.value.lora?.tx_enabled == true
        set(value) {
            updateLoraConfig { it.copy(tx_enabled = value) }
        }

    var region: Config.LoRaConfig.RegionCode
        get() = localConfig.value.lora?.region ?: Config.LoRaConfig.RegionCode.UNSET
        set(value) {
            updateLoraConfig { it.copy(region = value) }
        }

    private val _requestChannelSet = MutableStateFlow<ChannelSet?>(null)
    val requestChannelSet: StateFlow<ChannelSet?>
        get() = _requestChannelSet

    /**
     * Parse a channel URL string and store the resulting [ChannelSet].
     *
     * Accepts any string that [CommonUri.parse] can handle (e.g. the result of `android.net.Uri.toString()`).
     */
    fun requestChannelUrl(url: String, onError: () -> Unit) =
        runCatching { _requestChannelSet.value = CommonUri.parse(url).toChannelSet() }
            .onFailure { ex ->
                Logger.e(ex) { "Channel url error" }
                onError()
            }

    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    /** Applies a complete local channel set and succeeds only after a matching radio readback. */
    fun setChannels(channelSet: ChannelSet) = safeLaunch(tag = "setChannels") {
        if (_channelApplyState.value == ChannelApplyUiState.Applying) return@safeLaunch
        _channelApplyState.value = ChannelApplyUiState.Applying
        try {
            withContext(NonCancellable) {
                val result = channelReliabilityManager.applyAndVerify(channelSet)
                val state = result.toChannelApplyUiState()
                _channelApplyState.value = state
                if (state is ChannelApplyUiState.Failed) {
                    alertManager.showChannelApplyFailure(result)
                }
            }
        } finally {
            if (_channelApplyState.value == ChannelApplyUiState.Applying) {
                _channelApplyState.value = ChannelApplyUiState.WaitingForReconnect
            }
        }
    }

    fun protectCurrentChannels() = safeLaunch(tag = "protectCurrentChannels") {
        _channelOperationResult.value = channelReliabilityManager.protectCurrentChannelSet()
    }

    fun disableChannelProtection() = safeLaunch(tag = "disableChannelProtection") {
        _channelOperationResult.value = channelReliabilityManager.disableProtection()
    }

    fun clearChannelOperationResult() {
        _channelOperationResult.value = null
    }

    fun clearChannelApplyState() {
        _channelApplyState.value = ChannelApplyUiState.Idle
    }

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: Config) {
        safeLaunch(tag = "setConfig") { radioController.setLocalConfig(config) }
    }

    fun trackShare() {
        analytics.track("share", DataPair("content_type", "channel"))
    }

    private inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(localConfig.value.lora ?: Config.LoRaConfig())
        setConfig(Config(lora = data))
    }
}
