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
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.SessionManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleSettings
import kotlin.time.Duration.Companion.seconds

/** Ensures every connected MeshLink-managed radio has the canonical NTsocial channel installed. */
@Single
@Suppress("ReturnCount", "TooManyFunctions")
open class NtsocialChannelProvisioner(
    private val commandSender: CommandSender,
    private val radioConfigRepository: RadioConfigRepository,
    private val sessionManager: SessionManager,
    private val channelOperationLock: ChannelOperationLock,
    private val channelMutationLock: ChannelMutationLock,
    private val radioInterfaceService: RadioInterfaceService,
    private val ntsocialGatewayRepository: NtsocialGatewayRepository,
    private val meshConfigFlowManager: Lazy<MeshConfigFlowManager>,
) {
    open suspend fun ensureDefaultChannel(myNodeNum: Int, maxChannels: Int): NtsocialChannelProvisionResult =
        channelMutationLock.withLock { ensureDefaultChannelLocked(myNodeNum, maxChannels, expectedSession = null) }

    /** Returns null if this delayed handshake task no longer belongs to the configured active radio session. */
    open suspend fun ensureDefaultChannelForSession(
        myNodeNum: Int,
        maxChannels: Int,
        expectedRadioSessionEpoch: Long,
    ): NtsocialChannelProvisionResult? =
        ensureDefaultChannelForSession(myNodeNum, maxChannels, expectedRadioSessionEpoch, mutationLease = null)

    /** Composes provisioning inside a caller-owned mutation lease without recursively acquiring its mutex. */
    open suspend fun ensureDefaultChannelForSession(
        myNodeNum: Int,
        maxChannels: Int,
        expectedRadioSessionEpoch: Long,
        mutationLease: ChannelMutationLock.Lease?,
    ): NtsocialChannelProvisionResult? = channelMutationLock.withLease(mutationLease) {
        val expectedSession = captureExpectedSession(expectedRadioSessionEpoch) ?: return@withLease null
        ensureDefaultChannelLocked(myNodeNum, maxChannels, expectedSession).takeIf {
            isExpectedSessionCurrent(expectedSession)
        }
    }

    private suspend fun ensureDefaultChannelLocked(
        myNodeNum: Int,
        maxChannels: Int,
        expectedSession: ExpectedSession?,
    ): NtsocialChannelProvisionResult {
        if (!isExpectedSessionCurrent(expectedSession)) return NtsocialChannelProvisionResult.RadioRejected
        val defaultChannelSet = NtsocialDefaultChannel.channelSet
        val defaultSettings = defaultChannelSet.settings.firstOrNull()

        return if (defaultSettings == null) {
            NtsocialChannelProvisionResult.InvalidDefaultChannel
        } else {
            val currentChannelSet = radioConfigRepository.channelSetFlow.first()
            if (!isExpectedSessionCurrent(expectedSession)) return NtsocialChannelProvisionResult.RadioRejected
            val currentLocalConfig = radioConfigRepository.localConfigFlow.first()
            if (!isExpectedSessionCurrent(expectedSession)) return NtsocialChannelProvisionResult.RadioRejected
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

                !ensureLocalAdminSession(myNodeNum, expectedSession) -> {
                    Logger.w { "NTsocial channel provisioning skipped: local admin session timed out" }
                    NtsocialChannelProvisionResult.SessionTimeout
                }

                else -> applyProvisioning(myNodeNum, defaultLoraConfig, channelPlan.channel, expectedSession)
            }
        }
    }

    /** Finds the currently configured canonical NTsocial channel without exposing its PSK or other RF settings. */
    open suspend fun currentDefaultChannelIndex(): Int? = currentDefaultChannelIndex(mutationLease = null)

    open suspend fun currentDefaultChannelIndex(mutationLease: ChannelMutationLock.Lease?): Int? =
        channelMutationLock.withLease(mutationLease) {
            val defaultSettings = NtsocialDefaultChannel.channelSet.settings.firstOrNull() ?: return@withLease null
            radioConfigRepository.channelSetFlow
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
        val existingSettings = currentSettings.getOrNull(existingIndex)
        val desiredSettings =
            existingSettings?.let { current -> defaultSettings.preservingPositionPrecisionFrom(current) }
                ?: defaultSettings
        return when {
            existingSettings == desiredSettings -> ChannelPlanResult()

            existingSettings != null ->
                ChannelPlanResult(
                    channel =
                    ChannelPlan(
                        channel = desiredSettings.toChannel(index = existingIndex),
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

    /** Restores canonical channel fields without undoing the user's independently verified location-sharing policy. */
    private fun ChannelSettings.preservingPositionPrecisionFrom(current: ChannelSettings): ChannelSettings = copy(
        module_settings =
        (module_settings ?: ModuleSettings()).copy(
            position_precision = current.module_settings?.position_precision ?: 0,
        ),
    )

    private fun ChannelSettings.matchesNtsocial(defaultSettings: ChannelSettings): Boolean =
        name.equals(NtsocialDefaultChannel.CHANNEL_NAME, ignoreCase = true) ||
            (psk.size > 0 && psk == defaultSettings.psk)

    private fun ChannelSettings.toChannel(index: Int): Channel =
        Channel(index = index, role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY, settings = this)

    private fun shouldApplyDefaultLora(current: Config.LoRaConfig?): Boolean =
        current == null || current.region == Config.LoRaConfig.RegionCode.UNSET

    private suspend fun setLoraConfig(
        myNodeNum: Int,
        loraConfig: Config.LoRaConfig,
        expectedSession: ExpectedSession?,
    ): Boolean {
        if (!isExpectedSessionCurrent(expectedSession)) return false
        val config = Config(lora = loraConfig)
        val accepted = sendAdminAwait(myNodeNum, expectedSession) { AdminMessage(set_config = config) }
        if (!accepted || !isExpectedSessionCurrent(expectedSession)) return false
        return channelOperationLock.withLock {
            if (!isExpectedSessionCurrent(expectedSession)) return@withLock false
            radioConfigRepository.setLocalConfig(config)
            isExpectedSessionCurrent(expectedSession)
        }
    }

    private suspend fun setChannel(myNodeNum: Int, channel: Channel, expectedSession: ExpectedSession?): Boolean {
        if (!isExpectedSessionCurrent(expectedSession)) return false
        val accepted = sendAdminAwait(myNodeNum, expectedSession) { AdminMessage(set_channel = channel) }
        if (!accepted || !isExpectedSessionCurrent(expectedSession)) return false
        return channelOperationLock.withLock {
            if (!isExpectedSessionCurrent(expectedSession)) return@withLock false
            radioConfigRepository.updateChannelSettings(channel)
            isExpectedSessionCurrent(expectedSession)
        }
    }

    private suspend fun applyProvisioning(
        myNodeNum: Int,
        defaultLoraConfig: Config.LoRaConfig?,
        channelPlan: ChannelPlan?,
        expectedSession: ExpectedSession?,
    ): NtsocialChannelProvisionResult {
        val ingressClosed =
            channelOperationLock.withLock {
                if (!isExpectedSessionCurrent(expectedSession)) return@withLock false
                ntsocialGatewayRepository.invalidateInboundSession()
                true
            }
        if (!ingressClosed) return NtsocialChannelProvisionResult.RadioRejected
        val loraApplied = defaultLoraConfig?.let { setLoraConfig(myNodeNum, it, expectedSession) }
        val channelApplied =
            if (loraApplied == false) {
                null
            } else {
                channelPlan?.let { setChannel(myNodeNum, it.channel, expectedSession) }
            }

        return if (loraApplied == false || channelApplied == false) {
            NtsocialChannelProvisionResult.RadioRejected
        } else {
            if (expectedSession != null) {
                val expectedReadback = radioConfigRepository.channelSetFlow.first().normalizedReadback()
                val freshReadback = requestFreshReadback(expectedSession)
                if (freshReadback == null || !freshReadback.matches(expectedReadback)) {
                    Logger.w { "NTsocial channel provisioning fresh readback was missing or mismatched" }
                    return NtsocialChannelProvisionResult.ReadbackFailed
                }
            }
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

    private suspend fun ensureLocalAdminSession(myNodeNum: Int, expectedSession: ExpectedSession?): Boolean {
        if (!isExpectedSessionCurrent(expectedSession)) return false
        val alreadyActive = hasActiveSession(myNodeNum)
        if (!isExpectedSessionCurrent(expectedSession)) return false
        if (alreadyActive) return true

        val didRefresh =
            withTimeoutOrNull(ADMIN_SESSION_TIMEOUT) {
                coroutineScope {
                    val refreshWaiter =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            sessionManager.sessionRefreshFlow.filter { it == myNodeNum }.first()
                        }
                    try {
                        val becameActive = hasActiveSession(myNodeNum)
                        if (!isExpectedSessionCurrent(expectedSession)) {
                            false
                        } else if (becameActive) {
                            true
                        } else {
                            if (!isExpectedSessionCurrent(expectedSession)) return@coroutineScope false
                            val admitted =
                                sendAdminAwait(myNodeNum, expectedSession, wantResponse = true) {
                                    AdminMessage(get_device_metadata_request = true)
                                }
                            if (!admitted || !isExpectedSessionCurrent(expectedSession)) return@coroutineScope false
                            refreshWaiter.await()
                            isExpectedSessionCurrent(expectedSession)
                        }
                    } finally {
                        refreshWaiter.cancel()
                    }
                }
            } ?: false
        return didRefresh && isExpectedSessionCurrent(expectedSession)
    }

    private suspend fun sendAdminAwait(
        myNodeNum: Int,
        expectedSession: ExpectedSession?,
        wantResponse: Boolean = false,
        message: () -> AdminMessage,
    ): Boolean = channelOperationLock.withLock {
        if (!isExpectedSessionCurrent(expectedSession)) return@withLock false
        if (expectedSession == null) {
            commandSender.sendAdminAwait(myNodeNum, wantResponse = wantResponse, initFn = message)
        } else {
            commandSender.sendAdminAwaitForSession(
                expectedRadioSessionEpoch = expectedSession.epoch,
                destNum = myNodeNum,
                wantResponse = wantResponse,
                initFn = message,
            )
        }
    }

    private suspend fun requestFreshReadback(expectedSession: ExpectedSession): ChannelSet? = coroutineScope {
        val admission: Pair<Long?, Deferred<ChannelSet?>?> =
            channelOperationLock.withLock {
                if (!isExpectedSessionCurrent(expectedSession)) return@withLock null to null
                val manager = meshConfigFlowManager.value
                val requestToken = manager.beginChannelReadbackForSession(expectedSession.epoch)
                if (requestToken == null || !isExpectedSessionCurrent(expectedSession)) {
                    if (requestToken != null) {
                        manager.cancelChannelReadbackForSession(expectedSession.epoch, requestToken)
                    }
                    return@withLock null to null
                }
                val waiter =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(READBACK_TIMEOUT) {
                            manager.channelReadbackCompletion
                                .filter { completion ->
                                    completion?.requestToken == requestToken &&
                                        completion.radioSessionEpoch == expectedSession.epoch
                                }
                                .first()
                                ?.channelSet
                                ?.normalizedReadback()
                        }
                    }
                requestToken to waiter
            }
        val requestToken = admission.first ?: return@coroutineScope null
        val result = admission.second?.await()?.takeIf { isExpectedSessionCurrent(expectedSession) }
        if (result == null) {
            meshConfigFlowManager.value.cancelChannelReadbackForSession(expectedSession.epoch, requestToken)
        }
        result
    }

    private fun captureExpectedSession(expectedSessionEpoch: Long): ExpectedSession? =
        radioInterfaceService.radioSessionState.value.let { session ->
            session
                .takeIf { it.epoch == expectedSessionEpoch && it.isConfiguredReady }
                ?.selectedDeviceAddress
                ?.let { address -> ExpectedSession(epoch = expectedSessionEpoch, address = address) }
        }

    private fun isExpectedSessionCurrent(expectedSession: ExpectedSession?): Boolean = expectedSession == null ||
        radioInterfaceService.radioSessionState.value.let { session ->
            session.epoch == expectedSession.epoch &&
                session.selectedDeviceAddress == expectedSession.address &&
                session.activeDeviceAddress == expectedSession.address &&
                session.isConfiguredReady
        }

    private suspend fun hasActiveSession(myNodeNum: Int): Boolean =
        sessionManager.observeSessionStatus(myNodeNum).first() is SessionStatus.Active

    private data class ChannelPlan(val channel: Channel, val change: NtsocialChannelChange)

    private data class ChannelPlanResult(val channel: ChannelPlan? = null, val noSpace: Boolean = false)

    private data class ExpectedSession(val epoch: Long, val address: String)

    private companion object {
        val ADMIN_SESSION_TIMEOUT = 10.seconds
        val READBACK_TIMEOUT = 30.seconds
    }
}

sealed interface NtsocialChannelProvisionResult {
    data object AlreadyPresent : NtsocialChannelProvisionResult

    data object InvalidDefaultChannel : NtsocialChannelProvisionResult

    data object NoSpace : NtsocialChannelProvisionResult

    data object RadioRejected : NtsocialChannelProvisionResult

    data object SessionTimeout : NtsocialChannelProvisionResult

    data object ReadbackFailed : NtsocialChannelProvisionResult

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

        NtsocialChannelProvisionResult.ReadbackFailed ->
            NtsocialDefaultChannelStatus(
                ready = false,
                channelIndex = channelIndex,
                provisioningState = "READBACK_FAILED",
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

private fun ChannelSet.normalizedReadback(): ChannelSet {
    val normalizedSettings = settings.dropLastWhile { it == ChannelSettings() }
    return copy(settings = normalizedSettings)
}

private fun ChannelSet.matches(expected: ChannelSet): Boolean {
    val actual = normalizedReadback()
    val normalizedExpected = expected.normalizedReadback()
    return actual.settings == normalizedExpected.settings && actual.lora_config == normalizedExpected.lora_config
}
