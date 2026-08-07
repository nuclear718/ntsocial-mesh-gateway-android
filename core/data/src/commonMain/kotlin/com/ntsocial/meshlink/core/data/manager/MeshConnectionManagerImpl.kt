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
@file:Suppress("ReturnCount")

package com.ntsocial.meshlink.core.data.manager

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.handledLaunch
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.common.util.nowSeconds
import com.ntsocial.meshlink.core.data.ntsocial.NtsocialChannelProvisionResult
import com.ntsocial.meshlink.core.data.ntsocial.NtsocialChannelProvisioner
import com.ntsocial.meshlink.core.data.ntsocial.toDefaultChannelStatus
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.model.TelemetryType
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.DataPair
import com.ntsocial.meshlink.core.repository.HandshakeConstants
import com.ntsocial.meshlink.core.repository.HistoryManager
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.MqttManager
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import com.ntsocial.meshlink.core.repository.UiPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConnectionManagerImpl(
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val serviceNotifications: MeshServiceNotifications,
    private val uiPrefs: UiPrefs,
    private val packetHandler: PacketHandler,
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val mqttManager: MqttManager,
    private val historyManager: HistoryManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val commandSender: CommandSender,
    private val sessionManager: SessionManager,
    private val nodeManager: NodeManager,
    private val analytics: PlatformAnalytics,
    private val packetRepository: PacketRepository,
    private val workerManager: MeshWorkerManager,
    private val appWidgetUpdater: AppWidgetUpdater,
    private val heartbeatSender: DataLayerHeartbeatSender,
    private val ntsocialChannelProvisioner: NtsocialChannelProvisioner,
    private val ntsocialGatewayRepository: NtsocialGatewayRepository,
    private val channelReliabilityManager: ChannelReliabilityManager,
    private val channelOperationLock: ChannelOperationLock,
    private val channelMutationLock: ChannelMutationLock,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshConnectionManager {
    /**
     * Serializes [onConnectionChanged] to prevent TOCTOU races when multiple coroutines emit state transitions
     * concurrently (e.g. flow collector vs. sleep-timeout coroutine).
     */
    private val connectionMutex = Mutex()

    private var preHandshakeJob: Job? = null
    private var sleepTimeout: Job? = null
    private var handshakeTimeout: Job? = null
    private var connectTimeMsec = 0L
    private var connectionRestored = false

    private val locationReconcileMutex = Mutex()
    private val latestLocationRequest = MutableStateFlow(LocationRequest())
    private val _locationSharingRequested = MutableStateFlow(false)
    override val locationSharingRequested: StateFlow<Boolean> = _locationSharingRequested.asStateFlow()
    private val _shouldProvideLocation = MutableStateFlow(false)
    override val shouldProvideLocation: StateFlow<Boolean> = _shouldProvideLocation.asStateFlow()

    /** The node whose desired location callback is currently installed in [locationManager]. */
    @Volatile private var activeLocationNodeNum: Int? = null

    init {
        // Bridge transport-level state into the canonical app-level state.
        // This is the ONLY consumer of RadioInterfaceService.connectionState — it applies
        // light-sleep policy and handshake awareness before writing to ServiceRepository.
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState).launchIn(scope)

        // Ensure notification title and content stay in sync with state changes
        serviceRepository.connectionState.onEach { updateStatusNotification() }.launchIn(scope)

        scope.launch {
            try {
                appWidgetUpdater.updateAll()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Failed to kickstart LocalStatsWidget" }
            }
        }

        val nodeLocationPreference =
            nodeRepository.myNodeInfo.flatMapLatest { myNodeEntity ->
                if (myNodeEntity == null) {
                    flowOf(NodeLocationPreference())
                } else {
                    uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum).map { enabled ->
                        NodeLocationPreference(myNodeEntity.myNodeNum, enabled)
                    }
                }
            }

        combine(nodeLocationPreference, serviceRepository.connectionState, radioConfigRepository.localConfigFlow) {
                preference,
                connectionState,
                localConfig,
            ->
            LocationRequest(
                nodeNum = preference.nodeNum,
                preferenceEnabled = preference.enabled,
                connected = connectionState == ConnectionState.Connected,
                fixedPosition = localConfig.position?.fixed_position == true,
            )
        }
            .distinctUntilChanged()
            .onEach { request ->
                latestLocationRequest.value = request
                _locationSharingRequested.value = request.requested
                _shouldProvideLocation.value = request.shouldRun
                applyLocationRequest(request)
            }
            .launchIn(scope)
    }

    override fun reconcileLocation() {
        scope.handledLaunch { applyLocationRequest(latestLocationRequest.value) }
    }

    /** Serializes all start/restart/stop decisions so reconnects and node switches cannot race one another. */
    private suspend fun applyLocationRequest(request: LocationRequest) {
        locationReconcileMutex.withLock {
            val nodeNum = request.nodeNum
            if (!request.shouldRun || nodeNum == null) {
                activeLocationNodeNum = null
                locationManager.stop()
                return
            }

            if (activeLocationNodeNum != nodeNum) {
                if (activeLocationNodeNum != null) locationManager.stop()
                locationManager.start(scope) { position -> commandSender.sendPosition(position) }
                activeLocationNodeNum = nodeNum
            } else {
                // Permission grants and Android foreground-service promotion do not change the shared request tuple.
                // An explicit reconcile must still retry the saved callback without installing a second listener.
                locationManager.restart()
            }
        }
    }

    /**
     * Bridges a transport-level [ConnectionState] into the canonical app-level state.
     *
     * Applies light-sleep policy (power-saving / router role) to decide whether a [ConnectionState.DeviceSleep] event
     * should be surfaced as sleep or as a full disconnect, then delegates to [onConnectionChanged] for the actual state
     * transition.
     */
    private suspend fun onRadioConnectionState(newState: ConnectionState) {
        val localConfig = radioConfigRepository.localConfigFlow.first()
        val isRouter = localConfig.device?.role == Config.DeviceConfig.Role.ROUTER
        val lsEnabled = localConfig.power?.is_power_saving == true || isRouter

        val effectiveState =
            when (newState) {
                is ConnectionState.Connected -> ConnectionState.Connected

                is ConnectionState.DeviceSleep ->
                    if (lsEnabled) ConnectionState.DeviceSleep else ConnectionState.Disconnected

                is ConnectionState.Connecting -> ConnectionState.Connecting

                is ConnectionState.Disconnected -> ConnectionState.Disconnected
            }
        onConnectionChanged(effectiveState)
    }

    private suspend fun onConnectionChanged(c: ConnectionState) = connectionMutex.withLock {
        val current = serviceRepository.connectionState.value
        if (current == c) return@withLock

        // If the transport reports 'Connected', but we are already in the middle of a handshake (Connecting)
        if (c is ConnectionState.Connected && current is ConnectionState.Connecting) {
            Logger.d { "Ignoring redundant transport connection signal while handshake is in progress" }
            return@withLock
        }

        Logger.i { "onConnectionChanged: $current -> $c" }

        sleepTimeout?.cancel()
        sleepTimeout = null
        preHandshakeJob?.cancel()
        preHandshakeJob = null
        handshakeTimeout?.cancel()
        handshakeTimeout = null

        when (c) {
            is ConnectionState.Connecting -> serviceRepository.setConnectionState(ConnectionState.Connecting)
            is ConnectionState.Connected -> handleConnected()
            is ConnectionState.DeviceSleep -> handleDeviceSleep()
            is ConnectionState.Disconnected -> handleDisconnected()
        }
    }

    private fun handleConnected() {
        val radioSessionEpoch = radioInterfaceService.radioSessionState.value.epoch
        // Track whether this connection was restored from device sleep (vs. a fresh connect),
        // matching Apple's "connectionRestored" attribute for cross-platform DataDog parity.
        connectionRestored = serviceRepository.connectionState.value is ConnectionState.DeviceSleep
        // The service state remains 'Connecting' until config is fully loaded
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            serviceRepository.setConnectionState(ConnectionState.Connecting)
        }
        serviceBroadcasts.broadcastConnection()
        connectTimeMsec = nowMillis

        // Send a wake-up heartbeat before the config request. The firmware may be in a
        // power-saving state where the NimBLE callback context needs warming up. The 100ms
        // delay ensures the heartbeat BLE write is enqueued before the want_config_id
        // (sendToRadio is fire-and-forget through async coroutine launches).
        preHandshakeJob =
            scope.handledLaunch {
                channelOperationLock.withLock {
                    if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = false)) return@withLock
                    packetHandler.resumePacketQueueAndAwait()
                    if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = false)) return@withLock
                    heartbeatSender.sendHeartbeat("pre-handshake")
                    delay(PRE_HANDSHAKE_SETTLE_MS)
                    if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = false)) return@withLock
                    Logger.i { "Starting mesh handshake (Stage 1)" }
                    startConfigOnly()
                }
            }
    }

    private fun startHandshakeStallGuard(stage: Int, timeout: Duration, action: () -> Unit) {
        handshakeTimeout?.cancel()
        handshakeTimeout =
            scope.handledLaunch {
                delay(timeout)
                if (serviceRepository.connectionState.value is ConnectionState.Connecting) {
                    // Attempt one retry. Note: the firmware silently drops identical consecutive
                    // writes (per-connection dedup). If the first want_config_id was received and
                    // the stall is on our side, the retry will be dropped and the reconnect below
                    // will trigger instead — which is the right recovery in that case.
                    Logger.w {
                        "Handshake stall detected at Stage $stage — retrying, then reconnecting if still stalled"
                    }
                    action()
                    delay(HANDSHAKE_RETRY_TIMEOUT)
                    if (serviceRepository.connectionState.value is ConnectionState.Connecting) {
                        Logger.e { "Handshake still stalled after retry, forcing reconnect" }
                        onConnectionChanged(ConnectionState.Disconnected)
                    }
                }
            }
    }

    private suspend fun tearDownConnection() {
        ntsocialGatewayRepository.invalidateInboundSession()
        packetHandler.stopPacketQueueAndAwait()
        sessionManager.clearAll() // Prevent stale per-node passkeys on reconnect.
        locationReconcileMutex.withLock {
            activeLocationNodeNum = null
            locationManager.stop()
        }
        mqttManager.stop()
    }

    private suspend fun handleDeviceSleep() {
        serviceRepository.setConnectionState(ConnectionState.DeviceSleep)
        tearDownConnection()

        if (connectTimeMsec != 0L) {
            val now = nowMillis
            val duration = now - connectTimeMsec
            connectTimeMsec = 0L
            analytics.track(
                EVENT_CONNECTED_SECONDS,
                DataPair(EVENT_CONNECTED_SECONDS, duration.milliseconds.toDouble(DurationUnit.SECONDS)),
            )
        }

        sleepTimeout =
            scope.handledLaunch {
                try {
                    val localConfig = radioConfigRepository.localConfigFlow.first()
                    val rawTimeout = (localConfig.power?.ls_secs ?: 0) + DEVICE_SLEEP_TIMEOUT_SECONDS
                    // Cap the timeout so routers or power-saving configs (ls_secs=3600) don't
                    // leave the UI stuck in DeviceSleep for over an hour.
                    val timeout = rawTimeout.coerceAtMost(MAX_SLEEP_TIMEOUT_SECONDS)
                    Logger.d { "Waiting for sleeping device, timeout=$timeout secs (raw=$rawTimeout)" }
                    delay(timeout.seconds)
                    Logger.w { "Device timed out, setting disconnected" }
                    onConnectionChanged(ConnectionState.Disconnected)
                } catch (_: CancellationException) {
                    Logger.d { "device sleep timeout cancelled" }
                }
            }

        serviceBroadcasts.broadcastConnection()
    }

    private suspend fun handleDisconnected() {
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        tearDownConnection()

        analytics.track(
            EVENT_MESH_DISCONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
        )
        analytics.track(EVENT_NUM_NODES, DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size))

        serviceBroadcasts.broadcastConnection()
    }

    override fun startConfigOnly() {
        val action = { packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE)) }
        startHandshakeStallGuard(1, HANDSHAKE_TIMEOUT_STAGE1, action)
        action()
    }

    override fun startConfigOnlyForSession(expectedRadioSessionEpoch: Long): Boolean =
        packetHandler.sendToRadioForSession(
            ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE),
            expectedRadioSessionEpoch,
        )

    override fun startNodeInfoOnly() {
        val action = { packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE)) }
        startHandshakeStallGuard(2, HANDSHAKE_TIMEOUT_STAGE2, action)
        action()
    }

    override fun onRadioConfigLoaded() {
        val radioSessionEpoch = radioInterfaceService.radioSessionState.value.epoch
        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = false)) return
        scope.handledLaunch {
            if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = false)) return@handledLaunch
            val queuedPackets = packetRepository.getQueuedPackets()
            queuedPackets.forEach { packet ->
                if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = false)) return@handledLaunch
                try {
                    workerManager.enqueueSendMessage(packet.id)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "Failed to enqueue queued packet worker" }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun onNodeDbReady(radioSessionEpoch: Long): Boolean {
        // Bind every remaining completion side effect to the exact transport epoch. In particular, a stale completion
        // must not send admin traffic or start channel repair/provisioning against a replacement radio.
        if (!radioInterfaceService.markCurrentSessionConfigured(radioSessionEpoch)) {
            Logger.w { "Ignoring stale node-database completion for radio session $radioSessionEpoch" }
            return false
        }

        val myNodeNum = nodeManager.myNodeNum.value ?: 0

        // Set device time now that the full node picture is ready. Sending this during Stage 1
        // (onRadioConfigLoaded) introduced GATT write contention with the Stage 2 node-info burst. Exact-session
        // admission closes the same-address reconnect window between the configured check above and packet enqueue.
        commandSender.sendAdminAwaitForSession(radioSessionEpoch, myNodeNum) {
            AdminMessage(set_time_only = nowSeconds.toInt())
        }
        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return false

        // Proactively seed the session passkey. The firmware embeds session_passkey in every
        // admin *response* (wantResponse=true), but set_time_only has no response. A get_owner
        // request is the lightest way to trigger a response and populate the passkey cache so
        // that subsequent write operations don't fail with ADMIN_BAD_SESSION_KEY.
        commandSender.sendAdminAwaitForSession(
            expectedRadioSessionEpoch = radioSessionEpoch,
            destNum = myNodeNum,
            wantResponse = true,
        ) {
            AdminMessage(get_owner_request = true)
        }
        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return false

        // Cancel only after both exact-session startup admissions still prove ownership. A retired completion must not
        // continue into location, Gateway, MQTT, history, analytics, or telemetry side effects for its replacement.
        handshakeTimeout?.cancel()
        handshakeTimeout = null

        // Do not depend on MyNodeInfo emitting a distinct object after a reconnect to restore the location feed.
        reconcileLocation()
        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return false

        // These immediate requests are part of the just-completed handshake too. Keep their epoch check and queue
        // generation capture on the same admission boundary so a same-address replacement cannot receive them.
        commandSender.requestTelemetryForSession(
            expectedRadioSessionEpoch = radioSessionEpoch,
            requestId = commandSender.generatePacketId(),
            destNum = myNodeNum,
            typeValue = TelemetryType.LOCAL_STATS.ordinal,
        )
        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return false
        commandSender.requestTelemetryForSession(
            expectedRadioSessionEpoch = radioSessionEpoch,
            requestId = commandSender.generatePacketId(),
            destNum = myNodeNum,
            typeValue = TelemetryType.DEVICE.ordinal,
        )
        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return false

        scope.handledLaunch {
            channelMutationLock.withLock { mutationLease ->
                if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@withLock
                // Close ingress before the first admin command: firmware can apply a channel change before the local
                // DataStore mutation advances channelSnapshotGeneration. Only a fully completed exact-session sequence
                // may publish a replacement ingress identity.
                val ingressClosed =
                    channelOperationLock.withLock {
                        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) {
                            return@withLock false
                        }
                        ntsocialGatewayRepository.invalidateInboundSession()
                        true
                    }
                if (!ingressClosed) return@withLock
                // Reconcile the user-approved snapshot before the built-in channel provisioner can occupy a secondary
                // slot that is only temporarily missing. This keeps missing-only drift provable and conflict-safe.
                val repairResult =
                    channelReliabilityManager.reconcileProtectedChannelSetForSession(radioSessionEpoch, mutationLease)
                when (repairResult) {
                    ChannelReliabilityResult.REPAIRED -> Logger.i { "Restored missing protected secondary channels" }

                    ChannelReliabilityResult.CONFLICT ->
                        Logger.w { "Protected channel snapshot conflicts with the radio; automatic repair skipped" }

                    else -> Logger.d { "Protected channel reconciliation result=$repairResult" }
                }
                if (!repairResult.isChannelIdentitySafe()) {
                    Logger.w { "Gateway ingress remains closed after ambiguous channel repair result=$repairResult" }
                    return@withLock
                }
                if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@withLock

                val result =
                    ntsocialChannelProvisioner.ensureDefaultChannelForSession(
                        myNodeNum = myNodeNum,
                        maxChannels = nodeManager.getMyNodeInfo()?.maxChannels ?: DEFAULT_MAX_CHANNELS,
                        expectedRadioSessionEpoch = radioSessionEpoch,
                        mutationLease = mutationLease,
                    ) ?: return@withLock
                if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@withLock
                val defaultChannelIndex = ntsocialChannelProvisioner.currentDefaultChannelIndex(mutationLease)
                if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@withLock
                ntsocialGatewayRepository.updateDefaultChannelStatus(result.toDefaultChannelStatus(defaultChannelIndex))
                if (!result.isChannelIdentitySafe()) {
                    Logger.w { "Gateway ingress remains closed after ambiguous channel provisioning result=$result" }
                    return@withLock
                }
                val activated =
                    channelOperationLock.withLock {
                        isCurrentActiveSession(radioSessionEpoch, requireConfigured = true) &&
                            ntsocialGatewayRepository.activateInboundSession(radioSessionEpoch)
                    }
                if (!activated) {
                    Logger.w { "Gateway ingress remains closed because the final channel snapshot was not ready" }
                }
            }
        }

        // Start MQTT if enabled
        scope.handledLaunch {
            if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@handledLaunch
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@handledLaunch
            mqttManager.startProxy(
                moduleConfig.mqtt?.enabled == true,
                moduleConfig.mqtt?.proxy_to_client_enabled == true,
            )
        }

        if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return false
        reportConnection()

        // Request history
        scope.handledLaunch {
            if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@handledLaunch
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            if (!isCurrentActiveSession(radioSessionEpoch, requireConfigured = true)) return@handledLaunch
            moduleConfig.store_forward?.let {
                historyManager.requestHistoryReplay("onNodeDbReady", myNodeNum, it, "Unknown")
            }
        }

        return true
    }

    private fun ChannelReliabilityResult.isChannelIdentitySafe(): Boolean =
        this != ChannelReliabilityResult.RADIO_REJECTED && this != ChannelReliabilityResult.READBACK_FAILED

    private fun NtsocialChannelProvisionResult.isChannelIdentitySafe(): Boolean =
        this != NtsocialChannelProvisionResult.RadioRejected && this != NtsocialChannelProvisionResult.ReadbackFailed

    private fun isCurrentActiveSession(radioSessionEpoch: Long, requireConfigured: Boolean): Boolean {
        val session = radioInterfaceService.radioSessionState.value
        return session.epoch == radioSessionEpoch &&
            session.selectedDeviceAddress != null &&
            session.selectedDeviceAddress == session.activeDeviceAddress &&
            session.transportConnectionState == ConnectionState.Connected &&
            (!requireConfigured || session.configured)
    }

    private fun reportConnection() {
        val myNode = nodeManager.getMyNodeInfo()
        val radioModel = DataPair(KEY_RADIO_MODEL, myNode?.model ?: "unknown")
        analytics.track(
            EVENT_MESH_CONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
            radioModel,
        )

        // DataDog RUM custom action matching Apple's "connect" event for cross-platform analytics.
        val transportType = radioInterfaceService.getDeviceAddress()?.let { DeviceType.fromAddress(it)?.name }
        analytics.trackConnect(
            firmwareVersion = myNode?.firmwareVersion,
            transportType = transportType,
            hardwareModel = myNode?.model,
            nodes = nodeManager.nodeDBbyNodeNum.size,
            connectionRestored = connectionRestored,
        )
    }

    private data class NodeLocationPreference(val nodeNum: Int? = null, val enabled: Boolean = false)

    private data class LocationRequest(
        val nodeNum: Int? = null,
        val preferenceEnabled: Boolean = false,
        val connected: Boolean = false,
        val fixedPosition: Boolean = false,
    ) {
        val requested: Boolean
            get() = nodeNum != null && preferenceEnabled && !fixedPosition

        val shouldRun: Boolean
            get() = requested && connected
    }

    override fun updateTelemetry(t: Telemetry) {
        t.local_stats?.let { nodeRepository.updateLocalStats(it) }
        updateStatusNotification(t)
    }

    override fun updateStatusNotification(telemetry: Telemetry?) {
        serviceNotifications.updateServiceStateNotification(
            serviceRepository.connectionState.value,
            telemetry = telemetry,
        )
    }

    companion object {
        private const val DEVICE_SLEEP_TIMEOUT_SECONDS = 30

        // Maximum time (in seconds) to wait for a sleeping device before declaring it
        // disconnected, regardless of the device's ls_secs configuration. Without this
        // cap, routers (ls_secs=3600) leave the UI in DeviceSleep for over an hour.
        private const val MAX_SLEEP_TIMEOUT_SECONDS = 300

        /**
         * Delay between the pre-handshake heartbeat and the want_config_id send.
         *
         * Ensures the heartbeat BLE write completes and the firmware's NimBLE callback context is warmed up before the
         * config request arrives. 100ms is well above observed ESP32 task scheduling latency (~10–50ms) while adding
         * negligible connection latency.
         */
        private const val PRE_HANDSHAKE_SETTLE_MS = 100L

        private val HANDSHAKE_TIMEOUT_STAGE1 = 30.seconds

        /**
         * Stage 2 drains the full node database, which can be significantly larger than Stage 1 config on big meshes.
         * 60 s matches the meshtastic-client SDK timeout and avoids premature stall-guard triggers on meshes with 50+
         * nodes.
         */
        private val HANDSHAKE_TIMEOUT_STAGE2 = 60.seconds

        // Shorter window for the retry attempt: if the device genuinely didn't receive the
        // first want_config_id the retry completes within a few seconds. Waiting another 30s
        // before reconnecting just delays recovery unnecessarily.
        private val HANDSHAKE_RETRY_TIMEOUT = 15.seconds

        private const val DEFAULT_MAX_CHANNELS = 8

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
