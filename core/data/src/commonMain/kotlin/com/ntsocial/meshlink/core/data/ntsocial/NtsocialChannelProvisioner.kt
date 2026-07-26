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
package com.ntsocial.meshlink.core.data.ntsocial

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.model.SessionStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannel
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.time.Duration.Companion.seconds

/** Ensures every connected MeshLink-managed radio has the canonical NTsocial channel installed. */
@Single
open class NtsocialChannelProvisioner(
    private val commandSender: CommandSender,
    private val radioConfigRepository: RadioConfigRepository,
    private val sessionManager: SessionManager,
) {
    open suspend fun ensureDefaultChannel(myNodeNum: Int, maxChannels: Int): NtsocialChannelProvisionResult {
        val defaultChannelSet = NtsocialDefaultChannel.channelSet
        val defaultSettings = defaultChannelSet.settings.firstOrNull()

        return if (defaultSettings == null) {
            NtsocialChannelProvisionResult.InvalidDefaultChannel
        } else {
            val currentChannelSet = radioConfigRepository.channelSetFlow.first()
            val currentLocalConfig = radioConfigRepository.localConfigFlow.first()
            val channelPlan =
                buildChannelPlan(
                    currentSettings = currentChannelSet.settings,
                    defaultSettings = defaultSettings,
                    maxChannels = maxChannels.coerceAtLeast(1),
                )
            val defaultLoraConfig =
                defaultChannelSet.lora_config?.takeIf { shouldApplyDefaultLora(currentLocalConfig.lora) }

            when {
                channelPlan.noSpace -> {
                    Logger.w { "NTsocial channel provisioning skipped: no free channel slot" }
                    NtsocialChannelProvisionResult.NoSpace
                }

                channelPlan.channel == null && defaultLoraConfig == null -> {
                    Logger.d { "NTsocial channel already provisioned" }
                    NtsocialChannelProvisionResult.AlreadyPresent
                }

                !ensureLocalAdminSession(myNodeNum) -> {
                    Logger.w { "NTsocial channel provisioning skipped: local admin session timed out" }
                    NtsocialChannelProvisionResult.SessionTimeout
                }

                else -> applyProvisioning(myNodeNum, defaultLoraConfig, channelPlan.channel)
            }
        }
    }

    /** Finds the currently configured canonical NTsocial channel without exposing its PSK or other RF settings. */
    open suspend fun currentDefaultChannelIndex(): Int? {
        val defaultSettings = NtsocialDefaultChannel.channelSet.settings.firstOrNull() ?: return null
        return radioConfigRepository.channelSetFlow
            .first()
            .settings
            .indexOfFirst { it.matchesNtsocial(defaultSettings) }
            .takeIf { it >= 0 }
    }

    private fun buildChannelPlan(
        currentSettings: List<ChannelSettings>,
        defaultSettings: ChannelSettings,
        maxChannels: Int,
    ): ChannelPlanResult {
        val existingIndex = currentSettings.indexOfFirst { it.matchesNtsocial(defaultSettings) }
        return when {
            existingIndex >= 0 && currentSettings[existingIndex] == defaultSettings -> ChannelPlanResult()

            existingIndex >= 0 ->
                ChannelPlanResult(
                    channel =
                    ChannelPlan(
                        channel = defaultSettings.toChannel(index = existingIndex),
                        change = NtsocialChannelChange.UPDATED,
                    ),
                )

            currentSettings.size >= maxChannels -> ChannelPlanResult(noSpace = true)

            else ->
                ChannelPlanResult(
                    channel =
                    ChannelPlan(
                        channel = defaultSettings.toChannel(index = currentSettings.size),
                        change = NtsocialChannelChange.ADDED,
                    ),
                )
        }
    }

    private fun ChannelSettings.matchesNtsocial(defaultSettings: ChannelSettings): Boolean =
        name.equals(NtsocialDefaultChannel.CHANNEL_NAME, ignoreCase = true) ||
            (psk.size > 0 && psk == defaultSettings.psk)

    private fun ChannelSettings.toChannel(index: Int): Channel =
        Channel(index = index, role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY, settings = this)

    private fun shouldApplyDefaultLora(current: Config.LoRaConfig?): Boolean =
        current == null || current.region == Config.LoRaConfig.RegionCode.UNSET

    private suspend fun setLoraConfig(myNodeNum: Int, loraConfig: Config.LoRaConfig): Boolean {
        val config = Config(lora = loraConfig)
        val accepted = commandSender.sendAdminAwait(myNodeNum) { AdminMessage(set_config = config) }
        if (accepted) radioConfigRepository.setLocalConfig(config)
        return accepted
    }

    private suspend fun setChannel(myNodeNum: Int, channel: Channel): Boolean {
        val accepted = commandSender.sendAdminAwait(myNodeNum) { AdminMessage(set_channel = channel) }
        if (accepted) radioConfigRepository.updateChannelSettings(channel)
        return accepted
    }

    private suspend fun applyProvisioning(
        myNodeNum: Int,
        defaultLoraConfig: Config.LoRaConfig?,
        channelPlan: ChannelPlan?,
    ): NtsocialChannelProvisionResult {
        val loraApplied = defaultLoraConfig?.let { setLoraConfig(myNodeNum, it) }
        val channelApplied =
            if (loraApplied == false) {
                null
            } else {
                channelPlan?.let { setChannel(myNodeNum, it.channel) }
            }

        return if (loraApplied == false || channelApplied == false) {
            NtsocialChannelProvisionResult.RadioRejected
        } else {
            Logger.i {
                "NTsocial channel provisioned: channelChange=${channelPlan?.change}, loraApplied=${loraApplied == true}"
            }
            NtsocialChannelProvisionResult.Provisioned(
                channelIndex = channelPlan?.channel?.index,
                channelChange = channelPlan?.change ?: NtsocialChannelChange.NONE,
                loraConfigApplied = loraApplied == true,
            )
        }
    }

    private suspend fun ensureLocalAdminSession(myNodeNum: Int): Boolean {
        if (hasActiveSession(myNodeNum)) return true

        return withTimeoutOrNull(ADMIN_SESSION_TIMEOUT) {
            coroutineScope {
                val refreshed =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        sessionManager.sessionRefreshFlow.filter { it == myNodeNum }.first()
                    }
                try {
                    if (hasActiveSession(myNodeNum)) {
                        true
                    } else {
                        commandSender.sendAdmin(myNodeNum, wantResponse = true) {
                            AdminMessage(get_device_metadata_request = true)
                        }
                        refreshed.await()
                        true
                    }
                } finally {
                    refreshed.cancel()
                }
            }
        } ?: false
    }

    private suspend fun hasActiveSession(myNodeNum: Int): Boolean =
        sessionManager.observeSessionStatus(myNodeNum).first() is SessionStatus.Active

    private data class ChannelPlan(val channel: Channel, val change: NtsocialChannelChange)

    private data class ChannelPlanResult(val channel: ChannelPlan? = null, val noSpace: Boolean = false)

    private companion object {
        val ADMIN_SESSION_TIMEOUT = 10.seconds
    }
}

sealed interface NtsocialChannelProvisionResult {
    data object AlreadyPresent : NtsocialChannelProvisionResult

    data object InvalidDefaultChannel : NtsocialChannelProvisionResult

    data object NoSpace : NtsocialChannelProvisionResult

    data object RadioRejected : NtsocialChannelProvisionResult

    data object SessionTimeout : NtsocialChannelProvisionResult

    data class Provisioned(
        val channelIndex: Int?,
        val channelChange: NtsocialChannelChange,
        val loraConfigApplied: Boolean,
    ) : NtsocialChannelProvisionResult
}

enum class NtsocialChannelChange {
    NONE,
    ADDED,
    UPDATED,
    REPLACED,
}

/** Maps radio-provisioning outcomes to the sanitized status exposed by the external Gateway provider. */
internal fun NtsocialChannelProvisionResult.toDefaultChannelStatus(channelIndex: Int?): NtsocialDefaultChannelStatus =
    when (this) {
        NtsocialChannelProvisionResult.AlreadyPresent ->
            NtsocialDefaultChannelStatus(
                ready = channelIndex != null,
                channelIndex = channelIndex,
                provisioningState = "ALREADY_PRESENT",
                provisioningChannelChange = NtsocialChannelChange.NONE.name,
                provisioningLoraApplied = false,
            )

        NtsocialChannelProvisionResult.InvalidDefaultChannel ->
            NtsocialDefaultChannelStatus(provisioningState = "INVALID_DEFAULT_CHANNEL")

        NtsocialChannelProvisionResult.NoSpace -> NtsocialDefaultChannelStatus(provisioningState = "NO_SPACE")

        NtsocialChannelProvisionResult.RadioRejected ->
            NtsocialDefaultChannelStatus(
                ready = channelIndex != null,
                channelIndex = channelIndex,
                provisioningState = "RADIO_REJECTED",
            )

        NtsocialChannelProvisionResult.SessionTimeout ->
            NtsocialDefaultChannelStatus(
                ready = channelIndex != null,
                channelIndex = channelIndex,
                provisioningState = "SESSION_TIMEOUT",
            )

        is NtsocialChannelProvisionResult.Provisioned ->
            NtsocialDefaultChannelStatus(
                ready = channelIndex != null,
                channelIndex = channelIndex,
                provisioningState = "PROVISIONED",
                provisioningChannelChange = channelChange.name,
                provisioningLoraApplied = loraConfigApplied,
            )
    }
