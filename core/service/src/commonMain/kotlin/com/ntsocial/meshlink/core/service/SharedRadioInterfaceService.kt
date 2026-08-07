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
package com.ntsocial.meshlink.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.util.handledLaunch
import com.ntsocial.meshlink.core.common.util.ignoreExceptionSuspend
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.model.InterfaceId
import com.ntsocial.meshlink.core.model.MeshActivity
import com.ntsocial.meshlink.core.model.util.anonymize
import com.ntsocial.meshlink.core.network.repository.NetworkRepository
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.RadioSessionState
import com.ntsocial.meshlink.core.repository.RadioTransport
import com.ntsocial.meshlink.core.repository.RadioTransportFactory
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.Volatile

/**
 * Shared multiplatform connection orchestrator for Meshtastic radios.
 *
 * Manages the connection lifecycle (connect, active, disconnect, reconnect loop), device address state flows, and
 * hardware state observability (BLE/Network toggles). Delegates the actual raw byte transport mapping to a
 * platform-specific [RadioTransportFactory].
 */
@Suppress("LongParameterList", "TooManyFunctions")
@Single
class SharedRadioInterfaceService(
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
    private val radioPrefs: RadioPrefs,
    private val transportFactory: RadioTransportFactory,
    private val analytics: PlatformAnalytics,
) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType>
        get() = transportFactory.supportedDeviceTypes

    /**
     * Transport-level connection state reflecting the raw hardware link status.
     *
     * Updated directly by [onConnect] and [onDisconnect] when the physical transport (BLE, TCP, Serial) connects or
     * disconnects. This is consumed exclusively by
     * [MeshConnectionManager][com.ntsocial.meshlink.core.repository.MeshConnectionManager], which reconciles it into
     * the canonical app-level
     * [ServiceRepository.connectionState][com.ntsocial.meshlink.core.repository.ServiceRepository.connectionState].
     */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(radioPrefs.devAddr.value)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val _radioSessionState =
        MutableStateFlow(
            RadioSessionState(
                epoch = 1,
                selectedDeviceAddress = radioPrefs.devAddr.value,
                activeDeviceAddress = null,
                transportConnectionState = ConnectionState.Disconnected,
                configured = false,
            ),
        )
    override val radioSessionState: StateFlow<RadioSessionState> = _radioSessionState.asStateFlow()

    // Unbounded Channel preserves strict FIFO delivery of incoming radio bytes, which the
    // firmware handshake depends on (initial config packet ordering). A SharedFlow with
    // `launch { emit() }` per packet reorders under concurrent dispatch and breaks config load.
    // trySend on an UNLIMITED channel never suspends and never drops, so handleFromRadio can
    // remain a non-suspend synchronous callback.
    private val _receivedData = Channel<ByteArray>(Channel.UNLIMITED)
    override val receivedData: Flow<ByteArray> = _receivedData.receiveAsFlow()

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val meshActivity: Flow<MeshActivity> = _meshActivity.asFlow()

    private val _connectionError = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val connectionError: Flow<String> = _connectionError.asFlow()

    override val serviceScope: CoroutineScope
        get() = _serviceScope

    private var _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
    private var radioTransport: RadioTransport? = null
    private var runningTransportId: InterfaceId? = null
    private var isStarted = false

    /**
     * Set while [stopTransportLocked] is draining the polite disconnect frame. [sendToRadio] checks this so any late
     * traffic submitted after we've announced disconnection is dropped rather than racing in front of the firmware-side
     * link teardown.
     */
    @Volatile private var isStopping = false

    /**
     * True while an explicit connection lifecycle is active. It is set by [connect] or [setDeviceAddress] and cleared
     * by [disconnect]. Environmental state listeners and liveness recovery consult this gate so they cannot recreate a
     * transport after the user has explicitly disconnected.
     *
     * Every read and write is guarded by [transportMutex]. The @Volatile annotation keeps diagnostic reads honest.
     */
    @Volatile private var connectionRequested = false

    /** Prevents concurrent liveness-induced transport restarts from stacking. */
    private val isRestarting = atomic(false)

    /** Token owned by the one transport whose callbacks may currently mutate service state. */
    private val activeTransportGeneration = atomic(0L)

    /**
     * Makes transport-token validation and the corresponding synchronous callback side effect one atomic boundary. The
     * lock is deliberately never held while closing a transport or delaying a polite disconnect.
     */
    private val transportCallbackLock = SynchronizedObject()

    @Volatile
    @Suppress("MemberVisibilityCanBePrivate")
    internal var generationCallbackValidatedHook: (() -> Unit)? = null

    private val listenersInitialized = atomic(false)
    private var heartbeatJob: Job? = null
    private var lastHeartbeatMillis = 0L

    @Volatile private var lastDataReceivedMillis = 0L

    /**
     * Internal test seam for deterministic clock injection. Production uses [nowMillis]; tests override this to a
     * controllable clock so liveness checks, connection events, and received data all share one time source.
     */
    @Volatile
    @Suppress("MemberVisibilityCanBePrivate")
    internal var clockMillis: () -> Long = { nowMillis }

    private fun now(): Long = clockMillis()

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 30 * 1000L

        // If we haven't received any data from the radio within this window after sending a
        // heartbeat while the connection is nominally "Connected", the connection is likely a
        // zombie (BLE stack didn't report disconnect). Two missed heartbeat intervals gives
        // the firmware a reasonable window to respond or send telemetry.
        private const val LIVENESS_TIMEOUT_MILLIS = HEARTBEAT_INTERVAL_MILLIS * 2

        /**
         * Upper bound on how long we wait for the polite `ToRadio(disconnect = true)` frame to flush before tearing the
         * transport down. 500ms gives BLE's write-retry path (`BleRetry` backs off 500ms) room for one attempt on a
         * flaky GATT connection. Serial and TCP typically flush well under this window.
         */
        private const val POLITE_DISCONNECT_DRAIN_MS = 500L
    }

    private val initLock = Mutex()
    private val transportMutex = Mutex()

    /**
     * Invalidates every capability bound to the previous physical/configuration session before asynchronous teardown or
     * startup work begins. MutableStateFlow's atomic update keeps [RadioSessionState.epoch] monotonic across callback
     * and UI threads.
     */
    private fun beginRadioSession(
        selectedDeviceAddress: String? = _radioSessionState.value.selectedDeviceAddress,
        activeDeviceAddress: String? = _radioSessionState.value.activeDeviceAddress,
        transportConnectionState: ConnectionState,
    ) {
        _radioSessionState.update { current ->
            RadioSessionState(
                epoch = current.epoch + 1,
                selectedDeviceAddress = selectedDeviceAddress,
                activeDeviceAddress = activeDeviceAddress,
                transportConnectionState = transportConnectionState,
                configured = false,
            )
        }
    }

    @Suppress("ReturnCount")
    override fun markCurrentSessionConfigured(expectedEpoch: Long): Boolean {
        while (true) {
            val current = _radioSessionState.value
            if (
                current.epoch != expectedEpoch ||
                current.selectedDeviceAddress == null ||
                current.selectedDeviceAddress != current.activeDeviceAddress ||
                current.transportConnectionState != ConnectionState.Connected
            ) {
                return false
            }
            if (current.configured) return true
            if (_radioSessionState.compareAndSet(current, current.copy(configured = true))) return true
        }
    }

    private fun initStateListeners() {
        if (listenersInitialized.value) return
        processLifecycle.coroutineScope.launch {
            initLock.withLock {
                if (listenersInitialized.value) return@withLock
                listenersInitialized.value = true

                bluetoothRepository.state
                    .onEach { state ->
                        transportMutex.withLock {
                            if (state.enabled && connectionRequested) {
                                startTransportLocked()
                            } else if (runningTransportId == InterfaceId.BLUETOOTH) {
                                stopTransportLocked()
                            }
                        }
                    }
                    .catch { Logger.e(it) { "bluetoothRepository.state flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                networkRepository.networkAvailable
                    .onEach { state ->
                        transportMutex.withLock {
                            if (state && connectionRequested) {
                                startTransportLocked()
                            } else if (runningTransportId == InterfaceId.TCP) {
                                stopTransportLocked()
                            }
                        }
                    }
                    .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)
            }
        }
    }

    override fun connect() {
        processLifecycle.coroutineScope.launch {
            transportMutex.withLock {
                connectionRequested = true
                if (radioTransport == null) {
                    beginRadioSession(
                        selectedDeviceAddress = getBondedDeviceAddress(),
                        activeDeviceAddress = null,
                        transportConnectionState = ConnectionState.Connecting,
                    )
                }
                startTransportLocked()
            }
        }
        initStateListeners()
    }

    override suspend fun awaitHydratedDeviceAddress(): String? {
        val persisted = sanitizeDeviceAddress(radioPrefs.readPersistedDevAddr())
        transportMutex.withLock {
            check(!connectionRequested && radioTransport == null) {
                "Radio selection hydration must complete before connect"
            }
            if (_currentDeviceAddressFlow.value != persisted) {
                beginRadioSession(
                    selectedDeviceAddress = persisted,
                    activeDeviceAddress = null,
                    transportConnectionState = ConnectionState.Disconnected,
                )
                _currentDeviceAddressFlow.value = persisted
            }
        }
        return persisted
    }

    override suspend fun disconnect() {
        synchronized(transportCallbackLock) {
            activeTransportGeneration.incrementAndGet()
            beginRadioSession(
                selectedDeviceAddress = _currentDeviceAddressFlow.value,
                activeDeviceAddress = null,
                transportConnectionState = ConnectionState.Disconnected,
            )
        }
        transportMutex.withLock {
            connectionRequested = false
            ignoreExceptionSuspend { stopTransportLocked() }
        }
    }

    override fun isMockTransport(): Boolean = transportFactory.isMockTransport()

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        transportFactory.toInterfaceAddress(interfaceId, rest)

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    private fun getBondedDeviceAddress(): String? {
        val address = getDeviceAddress()
        return if (transportFactory.isAddressValid(address)) {
            address
        } else {
            null
        }
    }

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        val sanitized = sanitizeDeviceAddress(deviceAddr)

        if (getBondedDeviceAddress() == sanitized && isStarted && _connectionState.value == ConnectionState.Connected) {
            Logger.w { "Ignoring setBondedDevice ${sanitized?.anonymize}, already using that device" }
            return false
        }

        analytics.track("mesh_bond")

        Logger.d { "Setting bonded device to ${sanitized?.anonymize}" }
        synchronized(transportCallbackLock) {
            // Close the old callback capability synchronously with the public selection change. The async transport
            // teardown below must not leave a window where the retired radio can impersonate the new selection.
            activeTransportGeneration.incrementAndGet()
            beginRadioSession(
                selectedDeviceAddress = sanitized,
                activeDeviceAddress = null,
                transportConnectionState = ConnectionState.Disconnected,
            )
        }
        radioPrefs.setDevAddr(sanitized)
        _currentDeviceAddressFlow.value = sanitized

        processLifecycle.coroutineScope.launch {
            transportMutex.withLock {
                connectionRequested = sanitized != null
                ignoreExceptionSuspend { stopTransportLocked() }
                if (sanitized != null) {
                    startTransportLocked()
                }
            }
        }
        return true
    }

    private fun sanitizeDeviceAddress(deviceAddr: String?): String? =
        deviceAddr?.takeUnless { it == "n" || it.isBlank() }

    /** Must be called under [transportMutex]. */
    private fun startTransportLocked() {
        if (radioTransport != null) return

        // Never autoconnect to the simulated node. The mock transport may be offered in the
        // device-picker UI on debug builds, but it must only connect when the user explicitly
        // selects it (i.e. its address is stored in radioPrefs).
        val address = getBondedDeviceAddress()

        if (address == null) {
            Logger.d { "No valid address to connect to" }
            return
        }

        Logger.i { "Starting radio transport for ${address.anonymize}" }
        val transportGeneration =
            synchronized(transportCallbackLock) {
                beginRadioSession(
                    selectedDeviceAddress = address,
                    activeDeviceAddress = address,
                    transportConnectionState = ConnectionState.Connecting,
                )
                activeTransportGeneration.incrementAndGet()
            }
        isStarted = true
        runningTransportId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        radioTransport =
            transportFactory.createTransport(address, GenerationBoundRadioInterfaceService(transportGeneration))
        startHeartbeat()
    }

    /**
     * Must be called under [transportMutex].
     *
     * @param notifyPermanent Emits a permanent disconnect when true. Liveness recovery keeps the state transient.
     * @param sendPoliteDisconnect Sends a firmware disconnect frame when true. A zombie BLE link must not be written
     *   to.
     */
    private suspend fun stopTransportLocked(notifyPermanent: Boolean = true, sendPoliteDisconnect: Boolean = true) {
        // Invalidate first: close()/platform callbacks are allowed to arrive late and must not impersonate a
        // replacement transport that happens to share this service instance.
        val currentTransport =
            synchronized(transportCallbackLock) {
                activeTransportGeneration.incrementAndGet()
                radioTransport.also { transport ->
                    if (transport != null) {
                        beginRadioSession(
                            selectedDeviceAddress = _currentDeviceAddressFlow.value,
                            activeDeviceAddress = null,
                            transportConnectionState = ConnectionState.Disconnected,
                        )
                    }
                }
            }
        Logger.i { "Stopping transport $currentTransport" }
        // Best-effort polite goodbye: tell the firmware we're disconnecting on purpose so it can
        // tear down its side of the link cleanly instead of relying on timeouts / hardware events.
        // Flip isStopping before sending so any concurrent sendToRadio() drops incoming traffic —
        // we don't want normal packets racing behind the disconnect frame. Skip only when already
        // Disconnected; firmware can still consume the goodbye while handshaking or sleeping, so
        // it's worth sending in every other state. The send is fire-and-forget through the
        // transport's own scope; the drain delay gives async transports a window to flush before
        // close() cancels their write scope. BLE's retry path backs off 500ms, so this window
        // also covers one retry on flaky GATT links.
        if (
            sendPoliteDisconnect && currentTransport != null && _connectionState.value != ConnectionState.Disconnected
        ) {
            isStopping = true
            ignoreExceptionSuspend {
                currentTransport.handleSendToRadio(ToRadio(disconnect = true).encode())
                delay(POLITE_DISCONNECT_DRAIN_MS)
            }
        }
        isStarted = false
        radioTransport = null
        runningTransportId = null
        isStopping = false
        currentTransport?.close()

        _serviceScope.cancel("stopping transport")
        _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

        if (notifyPermanent && currentTransport != null) {
            handleCurrentTransportDisconnect(isPermanent = true, errorMessage = null)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        lastDataReceivedMillis = now()
        heartbeatJob =
            serviceScope.launch {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MILLIS)
                    keepAlive()
                    checkLiveness()
                }
            }
    }

    /**
     * Detects zombie connections where the BLE stack didn't report a disconnect.
     *
     * If we believe we're connected but haven't received any data from the radio within [LIVENESS_TIMEOUT_MILLIS], the
     * connection is likely dead. A stale BLE transport is silently recreated; other transports retain their own timeout
     * contracts and are not restarted merely because they are quiet.
     */
    internal fun checkLiveness() {
        if (_connectionState.value != ConnectionState.Connected) return

        val silenceMs = now() - lastDataReceivedMillis
        if (silenceMs > LIVENESS_TIMEOUT_MILLIS) {
            if (runningTransportId != InterfaceId.BLUETOOTH) {
                Logger.d { "Ignoring liveness timeout for non-BLE transport (silence: ${silenceMs}ms)" }
                return
            }

            Logger.w {
                "Liveness check failed: no data received for ${silenceMs}ms " +
                    "(threshold: ${LIVENESS_TIMEOUT_MILLIS}ms). Restarting BLE transport."
            }
            if (isRestarting.compareAndSet(expect = false, update = true)) {
                processLifecycle.coroutineScope.launch {
                    try {
                        transportMutex.withLock {
                            // The state may have changed while this restart waited for a user disconnect, a BLE state
                            // event, or an inbound packet. Revalidate every prerequisite before disrupting the link.
                            if (!canRestartBleForLiveness()) {
                                Logger.d { "Skipping stale BLE liveness restart" }
                                return@withLock
                            }

                            // This is intentionally silent: we recover the transport ourselves, so users should not
                            // receive a modal connection-timeout error for a transient condition.
                            onDisconnect(isPermanent = false)
                            ignoreExceptionSuspend {
                                stopTransportLocked(notifyPermanent = false, sendPoliteDisconnect = false)
                            }
                            startTransportLocked()
                        }
                    } finally {
                        isRestarting.value = false
                    }
                }
            }
        }
    }

    /** Must be called under [transportMutex]. */
    private fun canRestartBleForLiveness(): Boolean =
        isBleConnectionRequested() && isConnectedBleTransport() && isLivenessTimeoutElapsed()

    private fun isBleConnectionRequested(): Boolean = connectionRequested && bluetoothRepository.state.value.enabled

    private fun isConnectedBleTransport(): Boolean = runningTransportId == InterfaceId.BLUETOOTH &&
        radioTransport != null &&
        _connectionState.value == ConnectionState.Connected

    private fun isLivenessTimeoutElapsed(): Boolean = now() - lastDataReceivedMillis > LIVENESS_TIMEOUT_MILLIS

    fun keepAlive(now: Long = now()) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            radioTransport?.keepAlive()
            lastHeartbeatMillis = now
        }
    }

    override fun sendToRadio(bytes: ByteArray) {
        if (isStopping) {
            Logger.d { "sendToRadio: transport stopping, dropping ${bytes.size} bytes" }
            return
        }
        // Snapshot the transport to avoid calling handleSendToRadio on a null reference.
        // There is still a benign race: stopTransportLocked() may cancel _serviceScope
        // between the null-check and the launch, causing the coroutine to be silently
        // dropped. This is acceptable — if the transport is shutting down, dropping the
        // send is the correct behavior.
        val currentTransport =
            radioTransport
                ?: run {
                    Logger.w { "sendToRadio: no active radio transport, dropping ${bytes.size} bytes" }
                    return
                }
        enqueueTransportSend(currentTransport, bytes)
    }

    override fun sendToRadioForSession(bytes: ByteArray, expectedRadioSessionEpoch: Long): Boolean =
        synchronized(transportCallbackLock) {
            val session = _radioSessionState.value
            if (isStopping || session.epoch != expectedRadioSessionEpoch || !session.isConfiguredReady) {
                return@synchronized false
            }
            val currentTransport = radioTransport ?: return@synchronized false
            // Selection/stop rotates the session and transport token under this same lock. Therefore this send either
            // dispatches synchronously into E before retirement or observes F and rejects. Deferring this call to
            // _serviceScope would be unsafe for transports whose object survives an environmental reconnect: the old
            // job could otherwise execute only after that object had rebound itself to F.
            runCatching {
                currentTransport.handleSendToRadio(bytes)
                _meshActivity.tryEmit(MeshActivity.Send)
            }
                .onFailure { error -> Logger.e(error) { "Exact-session radio send failed" } }
                .isSuccess
        }

    private fun enqueueTransportSend(transport: RadioTransport, bytes: ByteArray) {
        _serviceScope.handledLaunch {
            transport.handleSendToRadio(bytes)
            _meshActivity.tryEmit(MeshActivity.Send)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleFromRadio(bytes: ByteArray) {
        synchronized(transportCallbackLock) { handleCurrentTransportData(bytes) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleCurrentTransportData(bytes: ByteArray) {
        try {
            lastDataReceivedMillis = now()
            // trySend synchronously onto the unbounded Channel so packet order matches arrival
            // order. The previous `launch { emit() }` pattern dispatched each packet onto a
            // fresh coroutine, letting the scheduler reorder them — which broke the firmware
            // config handshake (see PhoneAPI.cpp initial-handshake sequence).
            val result = _receivedData.trySend(bytes)
            if (result.isFailure) {
                Logger.e(result.exceptionOrNull()) { "Failed to enqueue ${bytes.size} received bytes; dropping packet" }
            }
            _meshActivity.tryEmit(MeshActivity.Receive)
        } catch (error: Exception) {
            Logger.e(error) { "handleFromRadio failed while emitting data" }
        }
    }

    override fun resetReceivedBuffer() {
        // Drain any bytes buffered while no collector was attached. Without this, a stop/start cycle
        // would replay stale bytes ahead of the next session's firmware handshake, since the channel
        // outlives the orchestrator's per-start scope.
        @Suppress("EmptyWhileBlock", "ControlFlowWithEmptyBody")
        while (_receivedData.tryReceive().isSuccess) Unit
    }

    override fun onConnect() {
        synchronized(transportCallbackLock) { handleCurrentTransportConnect() }
    }

    private fun handleCurrentTransportConnect() {
        // MutableStateFlow.value is thread-safe (backed by atomics) — assign directly rather than
        // launching a coroutine. The async launch pattern introduced a window where a concurrent
        // onDisconnect launch could execute AFTER an onConnect launch, leaving the service stuck
        // in Connected while the transport was actually disconnected.
        lastDataReceivedMillis = now()
        if (_radioSessionState.value.transportConnectionState != ConnectionState.Connected) {
            beginRadioSession(
                selectedDeviceAddress = _currentDeviceAddressFlow.value,
                activeDeviceAddress = _radioSessionState.value.activeDeviceAddress,
                transportConnectionState = ConnectionState.Connected,
            )
        }
        if (_connectionState.value != ConnectionState.Connected) {
            Logger.d { "Broadcasting connection state change to Connected" }
            _connectionState.value = ConnectionState.Connected
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
        synchronized(transportCallbackLock) { handleCurrentTransportDisconnect(isPermanent, errorMessage) }
    }

    private fun handleCurrentTransportDisconnect(isPermanent: Boolean, errorMessage: String?) {
        if (errorMessage != null) {
            processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionError.emit(errorMessage) }
        }
        val newTargetState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_radioSessionState.value.transportConnectionState != newTargetState) {
            beginRadioSession(
                selectedDeviceAddress = _currentDeviceAddressFlow.value,
                activeDeviceAddress = _radioSessionState.value.activeDeviceAddress,
                transportConnectionState = newTargetState,
            )
        }
        if (_connectionState.value != newTargetState) {
            Logger.d { "Broadcasting connection state change to $newTargetState" }
            _connectionState.value = newTargetState
        }
    }

    /** Callback facade captured by exactly one transport generation. */
    private inner class GenerationBoundRadioInterfaceService(private val transportGeneration: Long) :
        RadioInterfaceService by this@SharedRadioInterfaceService {
        override fun onConnect() {
            synchronized(transportCallbackLock) {
                if (transportGeneration == activeTransportGeneration.value) {
                    generationCallbackValidatedHook?.invoke()
                    handleCurrentTransportConnect()
                } else {
                    Logger.d { "Dropping retired transport onConnect callback" }
                }
            }
        }

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {
            synchronized(transportCallbackLock) {
                if (transportGeneration == activeTransportGeneration.value) {
                    generationCallbackValidatedHook?.invoke()
                    handleCurrentTransportDisconnect(isPermanent, errorMessage)
                } else {
                    Logger.d { "Dropping retired transport onDisconnect callback" }
                }
            }
        }

        override fun handleFromRadio(bytes: ByteArray) {
            synchronized(transportCallbackLock) {
                if (transportGeneration == activeTransportGeneration.value) {
                    generationCallbackValidatedHook?.invoke()
                    handleCurrentTransportData(bytes)
                } else {
                    Logger.d { "Dropping ${bytes.size} bytes from retired transport" }
                }
            }
        }
    }
}
