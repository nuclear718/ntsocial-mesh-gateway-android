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
@file:Suppress(
    "BinaryExpressionWrapping",
    "ClassSignature",
    "CyclomaticComplexMethod",
    "FunctionSignature",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MaxLineLength",
    "MultiLineIfElse",
    "ReturnCount",
)

package com.ntsocial.meshlink.core.data.manager

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.handledLaunch
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceVersion
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelReadbackCompletion
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.HandshakeConstants
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NotificationPrefs
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.NodeInfo
import com.ntsocial.meshlink.core.model.MyNodeInfo as SharedMyNodeInfo
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

internal enum class HandshakeCaptureResult {
    COLLECTED,
    FINALIZING,
    INACTIVE,
}

/** Collects one radio handshake's channels before publishing a single authoritative readback. */
@Single
class HandshakeChannelSetCollector(private val radioConfigRepository: RadioConfigRepository) {
    private data class PendingHandshake(
        val generation: Long,
        val channels: Map<Int, Channel> = emptyMap(),
        val loraConfig: Config.LoRaConfig? = null,
        val acceptingPackets: Boolean = true,
    )

    private val pending = atomic<PendingHandshake?>(null)
    private val persistenceMutex = Mutex()

    internal fun begin(generation: Long) {
        // Keep the last complete readback visible until this generation reaches config_complete. Clearing here would
        // turn an interrupted handshake into an empty/partial channel cache, which is precisely the state this
        // collector is intended to prevent.
        pending.value = PendingHandshake(generation = generation)
    }

    internal fun captureChannel(channel: Channel): HandshakeCaptureResult {
        var result: HandshakeCaptureResult? = null
        while (result == null) {
            val current = pending.value
            result =
                when {
                    current == null -> HandshakeCaptureResult.INACTIVE

                    !current.acceptingPackets -> HandshakeCaptureResult.FINALIZING

                    channel.index !in 0 until MAX_CHANNELS -> {
                        Logger.w { "Ignoring invalid handshake channel index=${channel.index}" }
                        HandshakeCaptureResult.COLLECTED
                    }

                    pending.compareAndSet(
                        current,
                        current.copy(channels = current.channels + (channel.index to channel)),
                    ) -> HandshakeCaptureResult.COLLECTED

                    else -> null
                }
        }
        return result
    }

    internal fun captureConfig(config: Config): HandshakeCaptureResult {
        var result: HandshakeCaptureResult? = null
        while (result == null) {
            val current = pending.value
            result =
                when {
                    current == null -> HandshakeCaptureResult.INACTIVE

                    !current.acceptingPackets -> HandshakeCaptureResult.FINALIZING

                    config.lora == null -> HandshakeCaptureResult.COLLECTED

                    pending.compareAndSet(current, current.copy(loraConfig = config.lora)) ->
                        HandshakeCaptureResult.COLLECTED

                    else -> null
                }
        }
        return result
    }

    internal fun finish(generation: Long): Boolean {
        var result: Boolean? = null
        while (result == null) {
            val current = pending.value
            result =
                when {
                    current == null -> false
                    current.generation != generation || !current.acceptingPackets -> false
                    pending.compareAndSet(current, current.copy(acceptingPackets = false)) -> true
                    else -> null
                }
        }
        return result
    }

    internal suspend fun commit(generation: Long): Boolean {
        return persistenceMutex.withLock {
            val current =
                pending.value?.takeIf { it.generation == generation && !it.acceptingPackets } ?: return@withLock false
            radioConfigRepository.replaceChannelSet(current.toChannelSet(), completeReadback = true)
            pending.compareAndSet(current, null)
        }
    }

    internal fun cancel(generation: Long) {
        while (true) {
            val current = pending.value ?: return
            if (current.generation != generation) return
            if (pending.compareAndSet(current, null)) return
        }
    }

    private fun PendingHandshake.toChannelSet(): ChannelSet {
        val lastEnabledIndex =
            channels.entries
                .filter { (_, channel) ->
                    channel.role == Channel.Role.PRIMARY || channel.role == Channel.Role.SECONDARY
                }
                .maxOfOrNull { it.key }
        val settings =
            if (lastEnabledIndex == null) {
                emptyList()
            } else {
                List(lastEnabledIndex + 1) { index ->
                    channels[index]
                        ?.takeIf { it.role == Channel.Role.PRIMARY || it.role == Channel.Role.SECONDARY }
                        ?.settings ?: ChannelSettings()
                }
            }
        return ChannelSet(settings = settings, lora_config = loraConfig)
    }

    private companion object {
        const val MAX_CHANNELS = 8
    }
}

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConfigFlowManagerImpl(
    private val nodeManager: NodeManager,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val radioInterfaceService: RadioInterfaceService,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val analytics: PlatformAnalytics,
    private val commandSender: CommandSender,
    private val heartbeatSender: DataLayerHeartbeatSender,
    private val notificationPrefs: NotificationPrefs,
    private val channelSetCollector: HandshakeChannelSetCollector,
    private val channelOperationLock: ChannelOperationLock,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshConfigFlowManager {
    private val wantConfigDelay = 100L

    /** Monotonically increasing generation so async clears from a stale handshake are discarded. */
    private val handshakeGeneration = atomic(0L)

    private data class PendingReadback(val epoch: Long, val requestToken: Long, val abandoned: Boolean = false)

    private val pendingReadback = atomic<PendingReadback?>(null)
    private val nextReadbackRequestToken = atomic(0L)
    private val _channelReadbackCompletion = MutableStateFlow<ChannelReadbackCompletion?>(null)
    override val channelReadbackCompletion: StateFlow<ChannelReadbackCompletion?> =
        _channelReadbackCompletion.asStateFlow()

    private enum class HandshakePurpose {
        FULL,
        CHANNEL_READBACK_ONLY,
    }

    /**
     * Type-safe handshake state machine. Each state carries exactly the data that is valid during that phase,
     * eliminating the possibility of accessing stale or uninitialized fields.
     *
     * Guards [handleConfigComplete] so that duplicate or out-of-order `config_complete_id` signals from the firmware
     * cannot trigger the wrong stage handler or drive the state machine backward.
     */
    private sealed class HandshakeState {
        /** No handshake in progress. */
        data object Idle : HandshakeState()

        /**
         * Stage 1: receiving device config, module config, channels, and metadata.
         *
         * [rawMyNodeInfo] arrives first (my_info packet); [metadata] may arrive shortly after. Both are consumed
         * together by [buildMyNodeInfo] at Stage 1 completion.
         */
        data class ReceivingConfig(
            val generation: Long,
            val radioSessionEpoch: Long,
            val rawMyNodeInfo: ProtoMyNodeInfo,
            val metadata: DeviceMetadata? = null,
            val purpose: HandshakePurpose = HandshakePurpose.FULL,
            val readbackRequestToken: Long? = null,
            val publishReadbackCompletion: Boolean = true,
        ) : HandshakeState()

        /** Stage 1 packets are frozen while the complete channel readback is persisted. */
        data class FinalizingConfig(val generation: Long, val radioSessionEpoch: Long, val purpose: HandshakePurpose) :
            HandshakeState()

        /**
         * Stage 2: receiving node-info packets from the firmware.
         *
         * [myNodeInfo] was committed at the Stage 1→2 transition. [nodes] accumulates [NodeInfo] packets until
         * `config_complete_id` arrives.
         */
        data class ReceivingNodeInfo(
            val radioSessionEpoch: Long,
            val myNodeInfo: SharedMyNodeInfo,
            val nodes: List<NodeInfo> = emptyList(),
        ) : HandshakeState()

        /** Both stages finished. The app is fully connected. */
        data class Complete(val radioSessionEpoch: Long, val myNodeInfo: SharedMyNodeInfo) : HandshakeState()
    }

    private var handshakeState: HandshakeState = HandshakeState.Idle

    override val newNodeCount: Int
        get() = (handshakeState as? HandshakeState.ReceivingNodeInfo)?.nodes?.size ?: 0

    override fun handleConfigComplete(configCompleteId: Int) {
        val state = handshakeState
        when (configCompleteId) {
            HandshakeConstants.CONFIG_NONCE -> {
                if (state !is HandshakeState.ReceivingConfig) {
                    Logger.w { "Ignoring Stage 1 config_complete in state=$state" }
                    return
                }
                if (state.purpose == HandshakePurpose.CHANNEL_READBACK_ONLY) {
                    val token = state.readbackRequestToken
                    val owner =
                        pendingReadback.value?.takeIf { pending ->
                            pending.epoch == state.radioSessionEpoch && pending.requestToken == token
                        }
                    if (owner == null || !pendingReadback.compareAndSet(owner, null)) {
                        channelSetCollector.cancel(state.generation)
                        handshakeState = HandshakeState.Idle
                        Logger.w { "Ignoring unowned channel-readback completion" }
                        return
                    }
                    handleConfigOnlyComplete(state.copy(publishReadbackCompletion = !owner.abandoned), state.purpose)
                    return
                }
                handleConfigOnlyComplete(state, state.purpose)
            }

            HandshakeConstants.NODE_INFO_NONCE -> {
                if (state !is HandshakeState.ReceivingNodeInfo) {
                    Logger.w { "Ignoring Stage 2 config_complete in state=$state" }
                    return
                }
                handleNodeInfoComplete(state)
            }

            else -> Logger.w { "Config complete id mismatch: $configCompleteId" }
        }
    }

    private fun handleConfigOnlyComplete(state: HandshakeState.ReceivingConfig, completionPurpose: HandshakePurpose) {
        Logger.i { "Config-only complete (Stage 1)" }

        val finalizedInfo = buildMyNodeInfo(state.rawMyNodeInfo, state.metadata)
        if (finalizedInfo == null) {
            handshakeState = HandshakeState.Idle
            if (state.purpose == HandshakePurpose.FULL) {
                Logger.w { "Stage 1 failed: could not build MyNodeInfo, retrying Stage 1" }
                scope.handledLaunch {
                    delay(wantConfigDelay)
                    connectionManager.value.startConfigOnly()
                }
            } else {
                Logger.w { "Channel readback failed: could not build MyNodeInfo; no full-handshake retry started" }
            }
            return
        }

        // Warn if firmware is below the absolute minimum supported version.
        // The UI layer already enforces this via FirmwareVersionCheck, so we just log here
        // for diagnostics rather than hard-disconnecting.
        finalizedInfo.firmwareVersion?.let { fwVersion ->
            if (DeviceVersion(fwVersion) < DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)) {
                Logger.w {
                    "Firmware $fwVersion is below minimum ${DeviceVersion.ABS_MIN_FW_VERSION} — " +
                        "protocol incompatibilities may occur"
                }
            }
        }

        if (!channelSetCollector.finish(state.generation)) {
            Logger.w { "Stage 1 channel readback was not current; waiting for the active handshake" }
            return
        }
        val purpose =
            if (completionPurpose == HandshakePurpose.CHANNEL_READBACK_ONLY) {
                HandshakePurpose.CHANNEL_READBACK_ONLY
            } else {
                state.purpose
            }
        handshakeState = HandshakeState.FinalizingConfig(state.generation, state.radioSessionEpoch, purpose)
        scope.handledLaunch {
            channelOperationLock.withLock {
                if (!isCurrentActiveRadioSession(state.radioSessionEpoch)) {
                    Logger.w { "Discarding stale Stage 1 completion for radio session ${state.radioSessionEpoch}" }
                    return@withLock
                }
                if (!channelSetCollector.commit(state.generation)) return@withLock
                val current = handshakeState
                if (!isCurrentFinalizingConfig(state, current)) {
                    return@withLock
                }
                if (purpose == HandshakePurpose.CHANNEL_READBACK_ONLY) {
                    val requestToken = state.readbackRequestToken ?: return@withLock
                    if (state.publishReadbackCompletion) {
                        _channelReadbackCompletion.value =
                            ChannelReadbackCompletion(
                                requestToken = requestToken,
                                radioSessionEpoch = state.radioSessionEpoch,
                                channelSet = radioConfigRepository.channelSetFlow.first(),
                            )
                    }
                    handshakeState = HandshakeState.Complete(state.radioSessionEpoch, finalizedInfo)
                    Logger.i { "Channel readback committed without starting NodeInfo or readiness side effects" }
                } else {
                    completeConfigStage(state.generation, state.radioSessionEpoch, finalizedInfo)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun isCurrentFinalizingConfig(expected: HandshakeState.ReceivingConfig, current: HandshakeState): Boolean {
        if (handshakeGeneration.value != expected.generation) return false
        if (current !is HandshakeState.FinalizingConfig) return false
        return current.generation == expected.generation &&
            current.radioSessionEpoch == expected.radioSessionEpoch &&
            current.purpose == expected.purpose &&
            isCurrentActiveRadioSession(expected.radioSessionEpoch)
    }

    private fun completeConfigStage(generation: Long, radioSessionEpoch: Long, finalizedInfo: SharedMyNodeInfo) {
        handshakeState =
            HandshakeState.ReceivingNodeInfo(radioSessionEpoch = radioSessionEpoch, myNodeInfo = finalizedInfo)
        Logger.i { "myNodeInfo committed (nodeNum=${finalizedInfo.myNodeNum})" }
        connectionManager.value.onRadioConfigLoaded()

        scope.handledLaunch {
            delay(wantConfigDelay)
            val heartbeatSent =
                channelOperationLock.withLock {
                    if (!isReceivingNodeInfo(generation, radioSessionEpoch)) return@withLock false
                    heartbeatSender.sendHeartbeat("inter-stage")
                    true
                }
            if (!heartbeatSent) return@handledLaunch
            delay(wantConfigDelay)
            channelOperationLock.withLock {
                if (!isReceivingNodeInfo(generation, radioSessionEpoch)) return@withLock
                Logger.i { "Requesting NodeInfo (Stage 2)" }
                connectionManager.value.startNodeInfoOnly()
            }
        }
    }

    private fun isReceivingNodeInfo(generation: Long, radioSessionEpoch: Long): Boolean {
        val state = handshakeState
        return handshakeGeneration.value == generation &&
            state is HandshakeState.ReceivingNodeInfo &&
            state.radioSessionEpoch == radioSessionEpoch &&
            isCurrentActiveRadioSession(radioSessionEpoch)
    }

    private fun handleNodeInfoComplete(state: HandshakeState.ReceivingNodeInfo) {
        Logger.i { "NodeInfo complete (Stage 2)" }

        val info = state.myNodeInfo

        // Transition state immediately (synchronously) to prevent duplicate handling.
        // The async work below rechecks the captured session while holding the shared radio/channel-operation lock.
        // Because nodes is now immutable, no additional snapshot is needed — state.nodes IS the snapshot.
        // Any stall-guard retry that re-enters handleNodeInfo will see Complete state and be ignored.
        handshakeState = HandshakeState.Complete(radioSessionEpoch = state.radioSessionEpoch, myNodeInfo = info)

        scope.handledLaunch {
            channelOperationLock.withLock {
                if (!isCurrentActiveRadioSession(state.radioSessionEpoch)) {
                    Logger.w { "Discarding stale Stage 2 completion for radio session ${state.radioSessionEpoch}" }
                    return@withLock
                }

                val entities =
                    state.nodes.mapNotNull { nodeInfo ->
                        nodeManager.installNodeInfo(nodeInfo, withBroadcast = false)
                        nodeManager.nodeDBbyNodeNum[nodeInfo.num]
                            ?: run {
                                Logger.w { "Node ${nodeInfo.num} missing from DB after installNodeInfo; skipping" }
                                null
                            }
                    }

                nodeRepository.installConfig(info, entities)
                if (!connectionManager.value.onNodeDbReady(state.radioSessionEpoch)) {
                    Logger.w { "Radio session changed before Stage 2 completion could be published" }
                    return@withLock
                }
                analytics.setDeviceAttributes(info.firmwareVersion ?: "unknown", info.model ?: "unknown")
                nodeManager.setNodeDbReady(true)
                nodeManager.setAllowNodeDbWrites(true)
                serviceRepository.setConnectionState(ConnectionState.Connected)
                serviceBroadcasts.broadcastConnection()
            }
        }
    }

    private fun isCurrentActiveRadioSession(expectedEpoch: Long): Boolean {
        val session = radioInterfaceService.radioSessionState.value
        return session.epoch == expectedEpoch &&
            session.selectedDeviceAddress != null &&
            session.selectedDeviceAddress == session.activeDeviceAddress &&
            session.transportConnectionState == ConnectionState.Connected
    }

    override fun handleMyInfo(myInfo: ProtoMyNodeInfo) {
        Logger.i { "MyNodeInfo received: ${myInfo.my_node_num}" }

        val gen = handshakeGeneration.incrementAndGet()
        val radioSessionEpoch = radioInterfaceService.radioSessionState.value.epoch
        while (true) {
            val owner = pendingReadback.value ?: break
            if (owner.epoch == radioSessionEpoch || pendingReadback.compareAndSet(owner, null)) break
        }
        val readback = pendingReadback.value?.takeIf { it.epoch == radioSessionEpoch }
        val purpose = if (readback == null) HandshakePurpose.FULL else HandshakePurpose.CHANNEL_READBACK_ONLY

        // Transition to Stage 1, discarding any stale data from a prior interrupted handshake.
        handshakeState =
            HandshakeState.ReceivingConfig(
                generation = gen,
                radioSessionEpoch = radioSessionEpoch,
                rawMyNodeInfo = myInfo,
                purpose = purpose,
                readbackRequestToken = readback?.requestToken,
            )
        channelSetCollector.begin(gen)
        if (purpose == HandshakePurpose.FULL) {
            nodeManager.setMyNodeNum(myInfo.my_node_num)
            nodeManager.setFirmwareEdition(myInfo.firmware_edition)
            applyEventFirmwareNotificationDefaults(myInfo.firmware_edition)
        }

        // ChannelSet has its own generation-bound clear/commit barrier. Other session caches still clear here.
        if (purpose == HandshakePurpose.FULL) {
            scope.handledLaunch {
                channelOperationLock.withLock {
                    if (handshakeGeneration.value != gen || !isCurrentActiveRadioSession(radioSessionEpoch)) {
                        return@withLock
                    }
                    radioConfigRepository.clearLocalConfig()
                    if (handshakeGeneration.value != gen || !isCurrentActiveRadioSession(radioSessionEpoch)) {
                        return@withLock
                    }
                    radioConfigRepository.clearLocalModuleConfig()
                    if (handshakeGeneration.value != gen || !isCurrentActiveRadioSession(radioSessionEpoch)) {
                        return@withLock
                    }
                    radioConfigRepository.clearDeviceUIConfig()
                    if (handshakeGeneration.value != gen || !isCurrentActiveRadioSession(radioSessionEpoch)) {
                        return@withLock
                    }
                    radioConfigRepository.clearFileManifest()
                }
            }
        }
    }

    override fun handleLocalMetadata(metadata: DeviceMetadata) {
        Logger.i { "Local Metadata received: ${metadata.firmware_version}" }
        val state = handshakeState
        if (state is HandshakeState.ReceivingConfig) {
            handshakeState = state.copy(metadata = metadata)
            // Persist the metadata immediately — buildMyNodeInfo() reads it at Stage 1 complete,
            // but the DB write does not need to wait until then.
            if (state.purpose == HandshakePurpose.FULL && metadata != DeviceMetadata()) {
                scope.handledLaunch {
                    channelOperationLock.withLock {
                        val current = handshakeState
                        if (
                            current is HandshakeState.ReceivingConfig &&
                            current.generation == state.generation &&
                            current.radioSessionEpoch == state.radioSessionEpoch &&
                            isCurrentActiveRadioSession(state.radioSessionEpoch)
                        ) {
                            nodeRepository.insertMetadata(state.rawMyNodeInfo.my_node_num, metadata)
                        }
                    }
                }
            }
        } else {
            Logger.w { "Ignoring metadata outside Stage 1 (state=$state)" }
        }
    }

    override fun handleNodeInfo(info: NodeInfo) {
        val state = handshakeState
        if (state is HandshakeState.ReceivingNodeInfo) {
            handshakeState = state.copy(nodes = state.nodes + info)
        } else {
            Logger.w { "Ignoring NodeInfo outside Stage 2 (state=$state)" }
        }
    }

    override fun handleFileInfo(info: FileInfo) {
        Logger.d { "FileInfo received: ${info.file_name} (${info.size_bytes} bytes)" }
        val epoch = handshakeState.radioSessionEpochOrNull() ?: radioInterfaceService.radioSessionState.value.epoch
        scope.handledLaunch {
            channelOperationLock.withLock {
                if (isCurrentActiveRadioSession(epoch)) radioConfigRepository.addFileInfo(info)
            }
        }
    }

    private fun HandshakeState.radioSessionEpochOrNull(): Long? = when (this) {
        HandshakeState.Idle -> null
        is HandshakeState.ReceivingConfig -> radioSessionEpoch
        is HandshakeState.FinalizingConfig -> radioSessionEpoch
        is HandshakeState.ReceivingNodeInfo -> radioSessionEpoch
        is HandshakeState.Complete -> radioSessionEpoch
    }

    override fun triggerWantConfig() {
        connectionManager.value.startConfigOnly()
    }

    override fun triggerWantConfigForSession(expectedRadioSessionEpoch: Long): Boolean =
        beginChannelReadbackForSession(expectedRadioSessionEpoch) != null

    override fun beginChannelReadbackForSession(expectedRadioSessionEpoch: Long): Long? {
        val session = radioInterfaceService.radioSessionState.value
        if (session.epoch != expectedRadioSessionEpoch || !session.isConfiguredReady) return null
        val state = handshakeState
        if (state !is HandshakeState.Complete || state.radioSessionEpoch != expectedRadioSessionEpoch) return null
        val reservation = PendingReadback(expectedRadioSessionEpoch, nextReadbackRequestToken.incrementAndGet())
        if (!pendingReadback.compareAndSet(null, reservation)) return null
        val admitted = connectionManager.value.startConfigOnlyForSession(expectedRadioSessionEpoch)
        if (!admitted) pendingReadback.compareAndSet(reservation, null)
        return reservation.requestToken.takeIf { admitted }
    }

    override fun cancelChannelReadbackForSession(expectedRadioSessionEpoch: Long, requestToken: Long) {
        while (true) {
            val owner = pendingReadback.value ?: return
            if (owner.epoch != expectedRadioSessionEpoch || owner.requestToken != requestToken || owner.abandoned) {
                return
            }
            if (pendingReadback.compareAndSet(owner, owner.copy(abandoned = true))) return
        }
    }

    /**
     * Builds a [SharedMyNodeInfo] from the raw proto and optional firmware metadata. Pure function — no side effects.
     * Returns null only if construction throws.
     */
    private fun buildMyNodeInfo(raw: ProtoMyNodeInfo, metadata: DeviceMetadata?): SharedMyNodeInfo? = try {
        with(raw) {
            SharedMyNodeInfo(
                myNodeNum = my_node_num,
                hasGPS = false,
                model =
                when (val hwModel = metadata?.hw_model) {
                    null,
                    HardwareModel.UNSET,
                    -> null

                    else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                },
                firmwareVersion = metadata?.firmware_version?.takeIf { it.isNotBlank() },
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = commandSender.getCurrentPacketId() and 0xffffffffL,
                messageTimeoutMsec = 300000,
                minAppVersion = min_app_version,
                maxChannels = 8,
                hasWifi = metadata?.hasWifi == true,
                channelUtilization = 0f,
                airUtilTx = 0f,
                // device_id is opaque bytes, not UTF-8. Hex preserves every byte and avoids replacement-character
                // collisions when it is used to partition an opt-in channel snapshot.
                deviceId = device_id.hex().ifEmpty { null },
                pioEnv = pio_env.ifEmpty { null },
            )
        }
    } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
        Logger.e(ex) { "Failed to build MyNodeInfo" }
        null
    }

    private fun applyEventFirmwareNotificationDefaults(edition: FirmwareEdition) {
        if (edition != FirmwareEdition.VANILLA) {
            if (!notificationPrefs.nodeEventsAutoDisabledForEvent.value) {
                notificationPrefs.setNodeEventsEnabled(false)
                notificationPrefs.setNodeEventsAutoDisabledForEvent(true)
            }
        } else {
            if (notificationPrefs.nodeEventsAutoDisabledForEvent.value) {
                notificationPrefs.setNodeEventsEnabled(true)
                notificationPrefs.setNodeEventsAutoDisabledForEvent(false)
            }
        }
    }
}
