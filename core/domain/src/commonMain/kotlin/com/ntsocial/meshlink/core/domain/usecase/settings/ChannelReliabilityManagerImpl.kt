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
package com.ntsocial.meshlink.core.domain.usecase.settings

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import com.ntsocial.meshlink.core.domain.usecase.session.EnsureSessionResult
import com.ntsocial.meshlink.core.model.ChannelSnapshotDrift
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.buildAuthoritativeChannelWrites
import com.ntsocial.meshlink.core.model.buildMissingSecondaryWrites
import com.ntsocial.meshlink.core.model.classifyChannelSnapshotDrift
import com.ntsocial.meshlink.core.model.normalizeReliableChannelSettings
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.time.Duration.Companion.seconds

/** Reliable, readback-verified local channel writer and conservative snapshot repair owner. */
@Single(binds = [ChannelReliabilityManager::class])
@Suppress("LongParameterList")
class ChannelReliabilityManagerImpl(
    private val commandSender: CommandSender,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val channelSnapshotRepository: ChannelSnapshotRepository,
    private val ensureRemoteAdminSession: EnsureRemoteAdminSessionUseCase,
    private val meshConfigFlowManager: Lazy<MeshConfigFlowManager>,
    private val operationLock: ChannelOperationLock,
    @Named("ServiceScope") serviceScope: CoroutineScope,
) : ChannelReliabilityManager {
    private val _isProtected = MutableStateFlow(false)
    override val isProtected: StateFlow<Boolean> = _isProtected

    private var lastReconciledGeneration: Pair<String, Long>? = null

    init {
        nodeRepository.myNodeInfo
            .onEach { info ->
                val identity = info?.stableDeviceIdentity()
                _isProtected.value = identity != null && channelSnapshotRepository.get(identity) != null
            }
            .launchIn(serviceScope)
    }

    override suspend fun applyAndVerify(channelSet: ChannelSet): ChannelReliabilityResult = operationLock.withLock {
        val context =
            currentRadioContext()
                ?: return@withLock unavailableContextResult(serviceRepository.connectionState.value)
        val currentLora = radioConfigRepository.localConfigFlow.first().lora
        val desiredLora = channelSet.lora_config ?: currentLora
        val settings = normalizeReliableChannelSettings(channelSet.settings, desiredLora)
        if (settings.isEmpty() || settings.first() == ChannelSettings() || settings.size > context.maxChannels) {
            return@withLock ChannelReliabilityResult.INVALID_CHANNEL_SET
        }
        val desired = ChannelSet(settings = settings, lora_config = desiredLora)
        val currentSnapshot = channelSnapshotRepository.get(context.identity)

        val result = applyTransaction(context, desired)
        if (result == ChannelReliabilityResult.VERIFIED && !isCurrentRadioContext(context)) {
            return@withLock ChannelReliabilityResult.READBACK_FAILED
        }
        if (result == ChannelReliabilityResult.VERIFIED && currentSnapshot != null) {
            channelSnapshotRepository.save(
                context.identity,
                ChannelProtectionSnapshot(maxChannels = context.maxChannels, channelSet = desired),
            )
            _isProtected.value = true
        }
        result
    }

    override suspend fun protectCurrentChannelSet(): ChannelReliabilityResult = operationLock.withLock {
        val context =
            currentRadioContext()
                ?: return@withLock unavailableContextResult(serviceRepository.connectionState.value)
        if (radioConfigRepository.channelReadbackGeneration.value <= 0L) {
            return@withLock ChannelReliabilityResult.READBACK_FAILED
        }
        val observed = radioConfigRepository.channelSetFlow.first().normalizedReadback()
        if (
            observed.settings.isEmpty() ||
            observed.settings.first() == ChannelSettings() ||
            observed.settings.size > context.maxChannels
        ) {
            return@withLock ChannelReliabilityResult.INVALID_CHANNEL_SET
        }
        channelSnapshotRepository.save(
            context.identity,
            ChannelProtectionSnapshot(maxChannels = context.maxChannels, channelSet = observed),
        )
        _isProtected.value = true
        ChannelReliabilityResult.PROTECTED
    }

    override suspend fun disableProtection(): ChannelReliabilityResult = operationLock.withLock {
        val context =
            currentRadioContext()
                ?: return@withLock unavailableContextResult(serviceRepository.connectionState.value)
        channelSnapshotRepository.clear(context.identity)
        _isProtected.value = false
        ChannelReliabilityResult.PROTECTION_DISABLED
    }

    override suspend fun reconcileProtectedChannelSet(): ChannelReliabilityResult = operationLock.withLock {
        val context =
            currentRadioContext()
                ?: return@withLock unavailableContextResult(serviceRepository.connectionState.value)
        val generation = radioConfigRepository.channelReadbackGeneration.value
        if (generation <= 0L) return@withLock ChannelReliabilityResult.READBACK_FAILED
        val attemptKey = context.identity to generation
        if (lastReconciledGeneration == attemptKey) {
            return@withLock ChannelReliabilityResult.NO_REPAIR_NEEDED
        }
        lastReconciledGeneration = attemptKey

        val snapshot =
            channelSnapshotRepository.get(context.identity)
                ?: return@withLock ChannelReliabilityResult.NO_SNAPSHOT.also { _isProtected.value = false }
        _isProtected.value = true
        val current = radioConfigRepository.channelSetFlow.first().normalizedReadback()
        when (
            classifyChannelSnapshotDrift(
                snapshotChannelSet = snapshot.channelSet,
                snapshotMaxChannels = snapshot.maxChannels,
                currentChannelSet = current,
                currentMaxChannels = context.maxChannels,
            )
        ) {
            ChannelSnapshotDrift.EXACT -> ChannelReliabilityResult.NO_REPAIR_NEEDED
            ChannelSnapshotDrift.CONFLICT -> ChannelReliabilityResult.CONFLICT
            ChannelSnapshotDrift.MISSING_SECONDARY_ONLY -> repairMissingSecondaries(context, snapshot, current)
        }
    }

    private suspend fun applyTransaction(context: RadioContext, desired: ChannelSet): ChannelReliabilityResult {
        val sessionResult = ensureRemoteAdminSession(context.nodeNum)
        return if (!sessionResult.isAvailable()) {
            ChannelReliabilityResult.SESSION_UNAVAILABLE
        } else {
            val beforeGeneration = radioConfigRepository.channelReadbackGeneration.value
            val commands = buildAuthoritativeChannelWrites(desired.settings, context.maxChannels)
            val committed =
                runEditTransaction(context) {
                    for (channel in commands) {
                        if (!sendVerifiedAdmin(context) { AdminMessage(set_channel = channel) }) {
                            return@runEditTransaction false
                        }
                    }
                    val currentLora = radioConfigRepository.localConfigFlow.first().lora
                    if (desired.lora_config != null && desired.lora_config != currentLora) {
                        if (
                            !sendVerifiedAdmin(context) {
                                AdminMessage(set_config = Config(lora = desired.lora_config))
                            }
                        ) {
                            return@runEditTransaction false
                        }
                    }
                    true
                }
            if (!committed) {
                ChannelReliabilityResult.RADIO_REJECTED
            } else {
                val readback = requestFreshReadback(context, beforeGeneration)
                if (readback != null && isCurrentRadioContext(context) && readback.matches(desired)) {
                    ChannelReliabilityResult.VERIFIED
                } else {
                    if (readback != null) {
                        Logger.w { "Channel transaction readback did not match the requested channel set" }
                    }
                    ChannelReliabilityResult.READBACK_FAILED
                }
            }
        }
    }

    private suspend fun repairMissingSecondaries(
        context: RadioContext,
        snapshot: ChannelProtectionSnapshot,
        current: ChannelSet,
    ): ChannelReliabilityResult {
        val sessionResult = ensureRemoteAdminSession(context.nodeNum)
        return if (!sessionResult.isAvailable()) {
            ChannelReliabilityResult.SESSION_UNAVAILABLE
        } else {
            val writes = buildMissingSecondaryWrites(snapshot.channelSet.settings, current.settings)
            if (writes.isEmpty()) {
                ChannelReliabilityResult.NO_REPAIR_NEEDED
            } else {
                val beforeGeneration = radioConfigRepository.channelReadbackGeneration.value
                val committed =
                    runEditTransaction(context) {
                        for (channel in writes) {
                            if (!sendVerifiedAdmin(context) { AdminMessage(set_channel = channel) }) {
                                return@runEditTransaction false
                            }
                        }
                        true
                    }
                if (!committed) {
                    ChannelReliabilityResult.RADIO_REJECTED
                } else {
                    val readback = requestFreshReadback(context, beforeGeneration)
                    val drift =
                        readback?.let {
                            classifyChannelSnapshotDrift(
                                snapshotChannelSet = snapshot.channelSet,
                                snapshotMaxChannels = snapshot.maxChannels,
                                currentChannelSet = it,
                                currentMaxChannels = context.maxChannels,
                            )
                        }
                    if (isCurrentRadioContext(context) && drift == ChannelSnapshotDrift.EXACT) {
                        ChannelReliabilityResult.REPAIRED
                    } else {
                        ChannelReliabilityResult.READBACK_FAILED
                    }
                }
            }
        }
    }

    /**
     * Runs one firmware edit-settings session and never leaves a successfully opened session without a commit attempt.
     * A cleanup commit closes the firmware session only; it cannot turn a failed write sequence into success.
     */
    private suspend fun runEditTransaction(context: RadioContext, writes: suspend () -> Boolean): Boolean {
        val opened = sendVerifiedAdmin(context) { AdminMessage(begin_edit_settings = true) }
        return if (!opened) {
            false
        } else {
            var committed = false
            try {
                if (writes()) {
                    committed = sendVerifiedAdmin(context) { AdminMessage(commit_edit_settings = true) }
                }
                committed
            } finally {
                if (!committed) {
                    withContext(NonCancellable) {
                        if (isCurrentRadioContext(context)) {
                            val closed =
                                runCatching { sendVerifiedAdmin(context) { AdminMessage(commit_edit_settings = true) } }
                                    .getOrDefault(false)
                            if (!closed) Logger.w { "Best-effort channel edit cleanup commit failed" }
                        } else {
                            Logger.w { "Skipped channel edit cleanup because the connected radio changed" }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendVerifiedAdmin(context: RadioContext, message: () -> AdminMessage): Boolean =
        coroutineScope {
            if (!isCurrentRadioContext(context)) return@coroutineScope false
            val destNum = context.nodeNum
            val requestId = commandSender.generatePacketId()
            val routingResult =
                async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(ROUTING_TIMEOUT) {
                        serviceRepository.meshPacketFlow
                            .filter { packet ->
                                val data = packet.decoded
                                data?.portnum == PortNum.ROUTING_APP &&
                                    data.request_id == requestId &&
                                    packet.from == destNum
                            }
                            .first()
                            .let { packet ->
                                runCatching { Routing.ADAPTER.decode(packet.decoded!!.payload) }
                                    .getOrNull()
                                    ?.error_reason == Routing.Error.NONE
                            }
                    } ?: false
                }
            try {
                if (!isCurrentRadioContext(context)) return@coroutineScope false
                val queued = commandSender.sendAdminAwait(destNum = destNum, requestId = requestId, initFn = message)
                queued && routingResult.await()
            } finally {
                routingResult.cancel()
            }
        }

    private suspend fun requestFreshReadback(context: RadioContext, afterGeneration: Long): ChannelSet? =
        coroutineScope {
            if (!isCurrentRadioContext(context)) return@coroutineScope null
            val waiter =
                async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(READBACK_TIMEOUT) {
                        radioConfigRepository.channelReadbackGeneration.filter { it > afterGeneration }.first()
                        radioConfigRepository.channelSetFlow.first().normalizedReadback()
                    }
                }
            if (!isCurrentRadioContext(context)) {
                waiter.cancel()
                return@coroutineScope null
            }
            meshConfigFlowManager.value.triggerWantConfig()
            waiter.await()?.takeIf { isCurrentRadioContext(context) }
        }

    private fun currentRadioContext(): RadioContext? {
        val connected = serviceRepository.connectionState.value == ConnectionState.Connected
        val info = nodeRepository.myNodeInfo.value
        val identity = info?.stableDeviceIdentity()
        val maxChannels = info?.maxChannels?.takeIf { it > 0 }
        return if (connected && info != null && identity != null && maxChannels != null) {
            RadioContext(nodeNum = info.myNodeNum, identity = identity, maxChannels = maxChannels)
        } else {
            null
        }
    }

    private fun isCurrentRadioContext(expected: RadioContext): Boolean = currentRadioContext() == expected

    private data class RadioContext(val nodeNum: Int, val identity: String, val maxChannels: Int)

    private companion object {
        val ROUTING_TIMEOUT = 15.seconds
        val READBACK_TIMEOUT = 30.seconds
    }
}

private fun EnsureSessionResult.isAvailable(): Boolean =
    this is EnsureSessionResult.AlreadyActive || this is EnsureSessionResult.Refreshed

private fun unavailableContextResult(connectionState: ConnectionState): ChannelReliabilityResult =
    if (connectionState != ConnectionState.Connected) {
        ChannelReliabilityResult.DISCONNECTED
    } else {
        ChannelReliabilityResult.IDENTITY_UNAVAILABLE
    }

private fun com.ntsocial.meshlink.core.model.MyNodeInfo.stableDeviceIdentity(): String? =
    deviceId?.trim()?.takeIf { it.isNotEmpty() }

private fun ChannelSet.normalizedReadback(): ChannelSet {
    val trimmed = settings.toMutableList()
    while (trimmed.size > 1 && trimmed.last() == ChannelSettings()) trimmed.removeLast()
    return copy(settings = trimmed)
}

private fun ChannelSet.matches(expected: ChannelSet): Boolean {
    val actual = normalizedReadback()
    val normalizedExpected = expected.normalizedReadback()
    return actual.settings == normalizedExpected.settings && actual.lora_config == normalizedExpected.lora_config
}
