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
@file:Suppress("CyclomaticComplexMethod", "ReturnCount")

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
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
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
@Suppress("ComplexCondition", "LongParameterList", "TooManyFunctions")
class ChannelReliabilityManagerImpl(
    private val commandSender: CommandSender,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val channelSnapshotRepository: ChannelSnapshotRepository,
    private val ensureRemoteAdminSession: EnsureRemoteAdminSessionUseCase,
    private val meshConfigFlowManager: Lazy<MeshConfigFlowManager>,
    private val operationLock: ChannelOperationLock,
    private val mutationLock: ChannelMutationLock,
    private val radioInterfaceService: RadioInterfaceService,
    private val ntsocialGatewayRepository: NtsocialGatewayRepository,
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

    override suspend fun applyAndVerify(channelSet: ChannelSet): ChannelReliabilityResult = mutationLock.withLock { _ ->
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
        if (!isCurrentRadioContext(context)) return@withLock ChannelReliabilityResult.SESSION_UNAVAILABLE
        val sessionResult = ensureRemoteAdminSession(context.nodeNum, context.radioSessionEpoch)
        if (!sessionResult.isAvailable() || !isCurrentRadioContext(context)) {
            return@withLock ChannelReliabilityResult.SESSION_UNAVAILABLE
        }

        val result = applyTransaction(context, desired)
        if (result != ChannelReliabilityResult.VERIFIED) return@withLock result

        val stableReadback =
            captureStableReadback(context) ?: return@withLock ChannelReliabilityResult.VERIFICATION_PENDING
        if (!stableReadback.channelSet.matches(desired)) {
            return@withLock if (isStableReadbackCurrent(context, stableReadback.generation)) {
                ChannelReliabilityResult.READBACK_FAILED
            } else {
                ChannelReliabilityResult.VERIFICATION_PENDING
            }
        }
        if (currentSnapshot != null) {
            val persisted =
                persistProtectionSnapshot(
                    context = context,
                    readbackGeneration = stableReadback.generation,
                    previous = currentSnapshot,
                    replacement = ChannelProtectionSnapshot(
                        maxChannels = context.maxChannels,
                        channelSet = desired,
                    ),
                )
            if (!persisted) return@withLock ChannelReliabilityResult.VERIFICATION_PENDING
            _isProtected.value = true
        }
        if (
            !isCurrentRadioContext(context) ||
            radioConfigRepository.channelReadbackGeneration.value != stableReadback.generation
        ) {
            return@withLock ChannelReliabilityResult.VERIFICATION_PENDING
        }
        if (!activateInboundSession(context)) {
            Logger.w { "Gateway ingress remains closed after verified channel apply because activation was stale" }
        }
        ChannelReliabilityResult.VERIFIED
    }

    override suspend fun protectCurrentChannelSet(): ChannelReliabilityResult = mutationLock.withLock { _ ->
        val context =
            currentRadioContext()
                ?: return@withLock unavailableContextResult(serviceRepository.connectionState.value)
        val previous = channelSnapshotRepository.get(context.identity)
        if (!isCurrentRadioContext(context)) return@withLock ChannelReliabilityResult.READBACK_FAILED
        val stableReadback =
            captureStableReadback(context) ?: return@withLock ChannelReliabilityResult.READBACK_FAILED
        val observed = stableReadback.channelSet
        if (
            observed.settings.isEmpty() ||
            observed.settings.first() == ChannelSettings() ||
            observed.settings.size > context.maxChannels
        ) {
            return@withLock ChannelReliabilityResult.INVALID_CHANNEL_SET
        }
        val persisted =
            persistProtectionSnapshot(
                context = context,
                readbackGeneration = stableReadback.generation,
                previous = previous,
                replacement = ChannelProtectionSnapshot(maxChannels = context.maxChannels, channelSet = observed),
            )
        if (!persisted) return@withLock ChannelReliabilityResult.READBACK_FAILED
        _isProtected.value = true
        ChannelReliabilityResult.PROTECTED
    }

    override suspend fun disableProtection(): ChannelReliabilityResult = mutationLock.withLock { _ ->
        val context =
            currentRadioContext()
                ?: return@withLock unavailableContextResult(serviceRepository.connectionState.value)
        channelSnapshotRepository.clear(context.identity)
        _isProtected.value = false
        ChannelReliabilityResult.PROTECTION_DISABLED
    }

    override suspend fun reconcileProtectedChannelSet(): ChannelReliabilityResult = reconcileProtectedChannelSet(null)

    override suspend fun reconcileProtectedChannelSetForSession(
        expectedRadioSessionEpoch: Long,
    ): ChannelReliabilityResult = reconcileProtectedChannelSet(expectedRadioSessionEpoch, mutationLease = null)

    override suspend fun reconcileProtectedChannelSetForSession(
        expectedRadioSessionEpoch: Long,
        mutationLease: ChannelMutationLock.Lease,
    ): ChannelReliabilityResult = reconcileProtectedChannelSet(expectedRadioSessionEpoch, mutationLease)

    private suspend fun reconcileProtectedChannelSet(
        expectedRadioSessionEpoch: Long?,
        mutationLease: ChannelMutationLock.Lease? = null,
    ): ChannelReliabilityResult = mutationLock.withLease(mutationLease) { _ ->
        val context =
            currentRadioContext()
                ?: return@withLease unavailableContextResult(serviceRepository.connectionState.value)
        // The configured-session check and context capture are intentionally one operation. Otherwise session E
        // can pass a preliminary check, reconnect, and then accidentally run the E-owned repair against the newly
        // captured session F (including the same physical address).
        if (expectedRadioSessionEpoch != null && context.radioSessionEpoch != expectedRadioSessionEpoch) {
            return@withLease ChannelReliabilityResult.SESSION_UNAVAILABLE
        }
        val snapshot =
            channelSnapshotRepository.get(context.identity)
                ?: return@withLease ChannelReliabilityResult.NO_SNAPSHOT.also { _isProtected.value = false }
        if (!isCurrentRadioContext(context)) return@withLease ChannelReliabilityResult.SESSION_UNAVAILABLE
        val stableReadback =
            captureStableReadback(context) ?: return@withLease ChannelReliabilityResult.READBACK_FAILED
        val attemptKey = context.identity to stableReadback.generation
        if (lastReconciledGeneration == attemptKey) {
            return@withLease ChannelReliabilityResult.NO_REPAIR_NEEDED
        }
        lastReconciledGeneration = attemptKey
        _isProtected.value = true
        val current = stableReadback.channelSet
        val result =
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
        if (
            expectedRadioSessionEpoch == null &&
            result == ChannelReliabilityResult.REPAIRED &&
            isCurrentRadioContext(context) &&
            !activateInboundSession(context)
        ) {
            Logger.w { "Gateway ingress remains closed after verified channel repair because activation was stale" }
        }
        result
    }

    private suspend fun captureStableReadback(context: RadioContext): StableReadback? {
        val generation = radioConfigRepository.channelReadbackGeneration.value
        if (generation <= 0L || !isCurrentRadioContext(context)) return null
        val channelSet = radioConfigRepository.channelSetFlow.first().normalizedReadback()
        return StableReadback(generation = generation, channelSet = channelSet).takeIf {
            isStableReadbackCurrent(context, generation)
        }
    }

    private fun isStableReadbackCurrent(context: RadioContext, generation: Long): Boolean =
        isCurrentRadioContext(context) && radioConfigRepository.channelReadbackGeneration.value == generation

    private suspend fun persistProtectionSnapshot(
        context: RadioContext,
        readbackGeneration: Long,
        previous: ChannelProtectionSnapshot?,
        replacement: ChannelProtectionSnapshot,
    ): Boolean {
        if (
            !isCurrentRadioContext(context) ||
            radioConfigRepository.channelReadbackGeneration.value != readbackGeneration
        ) {
            return false
        }
        channelSnapshotRepository.save(context.identity, replacement)
        if (
            isCurrentRadioContext(context) &&
            radioConfigRepository.channelReadbackGeneration.value == readbackGeneration
        ) {
            return true
        }

        // Environment-driven reconnects do not acquire ChannelOperationLock. Roll back a write that straddled such a
        // boundary so the retired session can neither replace nor create the user's protected snapshot.
        if (previous == null) {
            channelSnapshotRepository.clear(context.identity)
        } else {
            channelSnapshotRepository.save(context.identity, previous)
        }
        return false
    }

    private suspend fun applyTransaction(context: RadioContext, desired: ChannelSet): ChannelReliabilityResult {
        val commands = buildAuthoritativeChannelWrites(desired.settings, context.maxChannels)
        // Firmware can apply begin/channel/config writes before local readback advances the snapshot generation.
        // Close Gateway ingress at the last possible point before the first mutation admission.
        if (!closeIngressForMutation(context)) return ChannelReliabilityResult.SESSION_UNAVAILABLE
        val transactionResult =
            runEditTransaction(context) {
                for (channel in commands) {
                    val result = sendVerifiedAdmin(context) { AdminMessage(set_channel = channel) }
                    if (result != AdminCommandResult.ACKNOWLEDGED) {
                        return@runEditTransaction result
                    }
                }
                val currentLora = radioConfigRepository.localConfigFlow.first().lora
                if (desired.lora_config != null && desired.lora_config != currentLora) {
                    val result =
                        sendVerifiedAdmin(context) { AdminMessage(set_config = Config(lora = desired.lora_config)) }
                    if (result != AdminCommandResult.ACKNOWLEDGED) {
                        return@runEditTransaction result
                    }
                }
                AdminCommandResult.ACKNOWLEDGED
            }
        return when (transactionResult) {
            AdminCommandResult.REJECTED -> ChannelReliabilityResult.RADIO_REJECTED

            AdminCommandResult.UNCONFIRMED -> ChannelReliabilityResult.SESSION_UNAVAILABLE

            AdminCommandResult.ACKNOWLEDGED -> {
                val readback = requestFreshReadback(context)
                when {
                    readback == null || !isCurrentRadioContext(context) -> ChannelReliabilityResult.VERIFICATION_PENDING

                    readback.matches(desired) -> ChannelReliabilityResult.VERIFIED

                    else -> {
                        Logger.w { "Channel transaction readback did not match the requested channel set" }
                        ChannelReliabilityResult.READBACK_FAILED
                    }
                }
            }
        }
    }

    private suspend fun repairMissingSecondaries(
        context: RadioContext,
        snapshot: ChannelProtectionSnapshot,
        current: ChannelSet,
    ): ChannelReliabilityResult {
        val sessionResult = ensureRemoteAdminSession(context.nodeNum, context.radioSessionEpoch)
        return if (!sessionResult.isAvailable() || !isCurrentRadioContext(context)) {
            ChannelReliabilityResult.SESSION_UNAVAILABLE
        } else {
            val writes = buildMissingSecondaryWrites(snapshot.channelSet.settings, current.settings)
            if (writes.isEmpty()) {
                ChannelReliabilityResult.NO_REPAIR_NEEDED
            } else {
                // As with manual apply, no ingress identity may survive across the first firmware mutation and the
                // exact fresh readback that proves its final channel set.
                if (!closeIngressForMutation(context)) return ChannelReliabilityResult.SESSION_UNAVAILABLE
                val transactionResult =
                    runEditTransaction(context) {
                        for (channel in writes) {
                            val result = sendVerifiedAdmin(context) { AdminMessage(set_channel = channel) }
                            if (result != AdminCommandResult.ACKNOWLEDGED) {
                                return@runEditTransaction result
                            }
                        }
                        AdminCommandResult.ACKNOWLEDGED
                    }
                when (transactionResult) {
                    AdminCommandResult.REJECTED -> ChannelReliabilityResult.RADIO_REJECTED

                    AdminCommandResult.UNCONFIRMED -> ChannelReliabilityResult.SESSION_UNAVAILABLE

                    AdminCommandResult.ACKNOWLEDGED -> {
                        val readback = requestFreshReadback(context)
                        val drift =
                            readback?.let {
                                classifyChannelSnapshotDrift(
                                    snapshotChannelSet = snapshot.channelSet,
                                    snapshotMaxChannels = snapshot.maxChannels,
                                    currentChannelSet = it,
                                    currentMaxChannels = context.maxChannels,
                                )
                            }
                        when {
                            readback == null || !isCurrentRadioContext(context) ->
                                ChannelReliabilityResult.VERIFICATION_PENDING

                            drift == ChannelSnapshotDrift.EXACT -> ChannelReliabilityResult.REPAIRED

                            else -> ChannelReliabilityResult.READBACK_FAILED
                        }
                    }
                }
            }
        }
    }

    /**
     * Runs one firmware edit-settings session and never leaves a successfully opened session without a commit attempt.
     * A cleanup commit closes the firmware session only; it cannot turn a failed write sequence into success.
     */
    private suspend fun runEditTransaction(
        context: RadioContext,
        writes: suspend () -> AdminCommandResult,
    ): AdminCommandResult {
        val opened = sendVerifiedAdmin(context) { AdminMessage(begin_edit_settings = true) }
        return if (opened != AdminCommandResult.ACKNOWLEDGED) {
            opened
        } else {
            var result = AdminCommandResult.UNCONFIRMED
            try {
                val writeResult = writes()
                result =
                    if (writeResult == AdminCommandResult.ACKNOWLEDGED) {
                        sendVerifiedAdmin(context) { AdminMessage(commit_edit_settings = true) }
                    } else {
                        writeResult
                    }
                result
            } finally {
                if (result != AdminCommandResult.ACKNOWLEDGED) {
                    withContext(NonCancellable) {
                        if (isCurrentRadioContext(context)) {
                            val closed =
                                runCatching { sendVerifiedAdmin(context) { AdminMessage(commit_edit_settings = true) } }
                                    .getOrDefault(AdminCommandResult.UNCONFIRMED) == AdminCommandResult.ACKNOWLEDGED
                            if (!closed) Logger.w { "Best-effort channel edit cleanup commit failed" }
                        } else {
                            Logger.w { "Skipped channel edit cleanup because the connected radio changed" }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendVerifiedAdmin(context: RadioContext, message: () -> AdminMessage): AdminCommandResult =
        operationLock.withLock {
            coroutineScope {
                if (!isCurrentRadioContext(context)) return@coroutineScope AdminCommandResult.UNCONFIRMED
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
                                    val response =
                                        runCatching { Routing.ADAPTER.decode(packet.decoded!!.payload) }.getOrNull()
                                    when {
                                        response == null -> {
                                            Logger.w { "Matching channel-admin Routing response could not be decoded" }
                                            AdminCommandResult.UNCONFIRMED
                                        }

                                        response.error_reason == Routing.Error.NONE -> AdminCommandResult.ACKNOWLEDGED

                                        else -> AdminCommandResult.REJECTED
                                    }
                                }
                        } ?: AdminCommandResult.UNCONFIRMED
                    }
                try {
                    if (!isCurrentRadioContext(context)) return@coroutineScope AdminCommandResult.UNCONFIRMED
                    val queued =
                        commandSender.sendAdminAwaitForSession(
                            expectedRadioSessionEpoch = context.radioSessionEpoch,
                            destNum = destNum,
                            requestId = requestId,
                            initFn = message,
                        )
                    if (!queued || !isCurrentRadioContext(context)) {
                        return@coroutineScope AdminCommandResult.UNCONFIRMED
                    }
                    when (val routed = routingResult.await()) {
                        AdminCommandResult.ACKNOWLEDGED ->
                            if (isCurrentRadioContext(context)) routed else AdminCommandResult.UNCONFIRMED

                        else -> routed
                    }
                } finally {
                    routingResult.cancel()
                }
            }
        }

    private suspend fun requestFreshReadback(context: RadioContext): ChannelSet? = coroutineScope {
        val admission: Pair<Long?, Deferred<ChannelSet?>?> =
            operationLock.withLock {
                if (!isCurrentRadioContext(context)) return@withLock null to null
                val manager = meshConfigFlowManager.value
                val requestToken = manager.beginChannelReadbackForSession(context.radioSessionEpoch)
                if (requestToken == null || !isCurrentRadioContext(context)) {
                    if (requestToken != null) {
                        manager.cancelChannelReadbackForSession(context.radioSessionEpoch, requestToken)
                    }
                    return@withLock null to null
                }
                val waiter =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(READBACK_TIMEOUT) {
                            manager.channelReadbackCompletion
                                .filter { completion ->
                                    completion?.requestToken == requestToken &&
                                        completion.radioSessionEpoch == context.radioSessionEpoch
                                }
                                .first()
                                ?.channelSet
                                ?.normalizedReadback()
                        }
                    }
                requestToken to waiter
            }
        val requestToken = admission.first ?: return@coroutineScope null
        val result = admission.second?.await()?.takeIf { isCurrentRadioContext(context) }
        if (result == null) {
            meshConfigFlowManager.value.cancelChannelReadbackForSession(context.radioSessionEpoch, requestToken)
        }
        result
    }

    private suspend fun activateInboundSession(context: RadioContext): Boolean = operationLock.withLock {
        isCurrentRadioContext(context) &&
            ntsocialGatewayRepository.activateInboundSession(context.radioSessionEpoch)
    }

    private suspend fun closeIngressForMutation(context: RadioContext): Boolean = operationLock.withLock {
        if (!isCurrentRadioContext(context)) return@withLock false
        ntsocialGatewayRepository.invalidateInboundSession()
        true
    }

    private fun currentRadioContext(): RadioContext? {
        val connected = serviceRepository.connectionState.value == ConnectionState.Connected
        val capturedSession = radioInterfaceService.radioSessionState.value
        val selectedAddress = capturedSession.selectedDeviceAddress
        val info = nodeRepository.myNodeInfo.value
        val identity = info?.stableDeviceIdentity()
        val maxChannels = info?.maxChannels?.takeIf { it > 0 }
        val sessionStillCurrent = radioInterfaceService.radioSessionState.value == capturedSession
        return if (
            connected &&
            sessionStillCurrent &&
            capturedSession.isConfiguredReady &&
            selectedAddress != null &&
            info != null &&
            identity != null &&
            maxChannels != null
        ) {
            RadioContext(
                nodeNum = info.myNodeNum,
                identity = identity,
                maxChannels = maxChannels,
                radioSessionEpoch = capturedSession.epoch,
                radioAddress = selectedAddress,
            )
        } else {
            null
        }
    }

    private fun isCurrentRadioContext(expected: RadioContext): Boolean = currentRadioContext() == expected

    private data class RadioContext(
        val nodeNum: Int,
        val identity: String,
        val maxChannels: Int,
        val radioSessionEpoch: Long,
        val radioAddress: String,
    )

    private data class StableReadback(val generation: Long, val channelSet: ChannelSet)

    private enum class AdminCommandResult {
        ACKNOWLEDGED,
        REJECTED,
        UNCONFIRMED,
    }

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
