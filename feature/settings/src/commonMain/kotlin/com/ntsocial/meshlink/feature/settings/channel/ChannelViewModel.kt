/*
 * Copyright (c) 2026 Meshtastic LLC
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
import com.ntsocial.meshlink.core.repository.DataPair
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.ui.util.getChannelList
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig

@KoinViewModel
class ChannelViewModel(
    private val radioController: RadioController,
    private val radioConfigRepository: RadioConfigRepository,
    private val analytics: PlatformAnalytics,
) : ViewModel() {

    val connectionState = radioController.connectionState

    val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

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

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: ChannelSet) = safeLaunch(tag = "setChannels") {
        getChannelList(channelSet.settings, channels.value.settings).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settings)

        val newLoraConfig = channelSet.lora_config
        if (localConfig.value.lora != newLoraConfig) {
            setConfig(Config(lora = newLoraConfig))
        }
    }

    fun setChannel(channel: Channel) {
        safeLaunch(tag = "setChannel") { radioController.setLocalChannel(channel) }
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
