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

import android.app.AppOpsManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.hasLocationPermission
import com.ntsocial.meshlink.core.common.isLocationEnabled
import com.ntsocial.meshlink.core.common.util.registerReceiverCompat
import com.ntsocial.meshlink.core.common.util.toRemoteExceptions
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.DeviceVersion
import com.ntsocial.meshlink.core.model.MeshUser
import com.ntsocial.meshlink.core.model.MyNodeInfo
import com.ntsocial.meshlink.core.model.NodeInfo
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.model.RadioNotConnectedException
import com.ntsocial.meshlink.core.model.util.anonymize
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshRouter
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.SERVICE_NOTIFY_ID
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import org.meshtastic.proto.PortNum
import kotlin.concurrent.Volatile

/**
 * Android foreground service that hosts the Meshtastic mesh radio connection.
 *
 * Acts as the lifecycle anchor for the [MeshServiceOrchestrator], which manages all manager initialization and
 * connection state. Exposes an AIDL binder for external client integration via [core:api].
 */
// IMeshService is deprecated but still required for AIDL binding
@Suppress("TooManyFunctions", "LargeClass", "DEPRECATION")
class MeshService : Service() {

    private val radioInterfaceService: RadioInterfaceService by inject()

    private val serviceRepository: ServiceRepository by inject()

    private val serviceBroadcasts: ServiceBroadcasts by inject()

    private val nodeManager: NodeManager by inject()

    private val commandSender: CommandSender by inject()

    private val locationManager: MeshLocationManager by inject()

    private val connectionManager: MeshConnectionManager by inject()

    private val processLifecycle: Lifecycle by inject(qualifier = named("ProcessLifecycle"))

    private val notifications: MeshServiceNotifications by inject()

    /** Android-typed accessor for the foreground service notification. */
    private val androidNotifications: MeshServiceNotificationsImpl
        get() = notifications as MeshServiceNotificationsImpl

    private val orchestrator: MeshServiceOrchestrator by inject()

    private val router: MeshRouter by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val serviceJob = Job()
    private val serviceScope by lazy { CoroutineScope(dispatchers.io + serviceJob) }

    private var isServiceInitialized = false
    private var pendingDeviceSelectionStopJob: Job? = null
    private val foregroundStateLock = Any()

    @Volatile private var isForegroundStarted = false

    @Volatile private var effectiveForegroundServiceType = 0

    private var processLifecycleObserverRegistered = false
    private var locationModeReceiverRegistered = false
    private var locationPermissionObserverRegistered = false

    private val appOpsManager: AppOpsManager by lazy { getSystemService(AppOpsManager::class.java) }

    private val processLifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                reconcileLocationForegroundAccess()
            }
        }

    private val locationModeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (
                    intent?.action == LocationManager.MODE_CHANGED_ACTION ||
                    intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION
                ) {
                    reconcileLocationForegroundAccess()
                }
            }
        }

    private val locationPermissionChangedListener =
        AppOpsManager.OnOpChangedListener { operation, changedPackage ->
            if (
                changedPackage == packageName &&
                (operation == AppOpsManager.OPSTR_FINE_LOCATION || operation == AppOpsManager.OPSTR_COARSE_LOCATION)
            ) {
                reconcileLocationForegroundAccess()
            }
        }

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: throw RadioNotConnectedException()

    companion object {
        fun actionReceived(portNum: Int): String {
            val portType = PortNum.fromValue(portNum)
            val portStr = portType?.toString() ?: portNum.toString()
            return actionReceived(portStr)
        }

        fun createIntent(context: Context) = Intent(context, MeshService::class.java)

        fun changeDeviceAddress(context: Context, service: IMeshService, address: String?) {
            service.setDeviceAddress(address)
            startService(context)
        }

        val minDeviceVersion = DeviceVersion(DeviceVersion.MIN_FW_VERSION)
        val absoluteMinDeviceVersion = DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Creating mesh service" }

        try {
            orchestrator.start()
            isServiceInitialized = true
            connectionManager.locationSharingRequested
                .onEach { reconcileLocationForegroundAccess() }
                .launchIn(serviceScope)
            registerLocationReconcileSignals()
        } catch (e: IllegalStateException) {
            // Koin throws IllegalStateException when the DI graph is not yet initialized.
            // This can happen if the system restarts the service (e.g. after a crash or on boot)
            // before Application.onCreate() has finished setting up Koin.
            // In release builds, R8 may merge Koin's InstanceCreationException with unrelated
            // exception classes (observed as io.ktor.http.URLDecodeException), so we cannot rely
            // on the exception type alone. We catch IllegalStateException narrowly around the
            // orchestrator/DI access — not around super.onCreate() — so framework exceptions
            // still propagate normally.
            Logger.e(e) { "MeshService: DI not ready, stopping service" }
            stopSelf()
            return
        }
    }

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceInitialized) {
            Logger.w { "onStartCommand called but service is not initialized (likely DI failure). Stopping." }
            stopSelf()
            return START_NOT_STICKY
        }

        val deviceAddress = radioInterfaceService.getDeviceAddress()

        connectionManager.updateStatusNotification()
        val notification = androidNotifications.getServiceNotification()

        if (!startForegroundForCurrentState(notification)) {
            Logger.w { "MeshService could not enter the foreground; stopping instead of leaving a pending start" }
            locationManager.setLocationAccessAllowed(false)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        connectionManager.reconcileLocation()

        pendingDeviceSelectionStopJob?.cancel()
        pendingDeviceSelectionStopJob = null
        if (
            meshServiceStartupDecision(deviceAddress, graceElapsed = false) == MeshServiceStartupDecision.AWAIT_DEVICE
        ) {
            Logger.i { "Mesh service is awaiting persisted device selection before deciding whether to stop" }
            pendingDeviceSelectionStopJob =
                serviceScope.launch {
                    delay(DEVICE_SELECTION_STARTUP_GRACE_MS)
                    if (
                        meshServiceStartupDecision(radioInterfaceService.getDeviceAddress(), graceElapsed = true) ==
                        MeshServiceStartupDecision.STOP
                    ) {
                        Logger.i { "Stopping mesh service because no device was selected after startup grace" }
                        ServiceCompat.stopForeground(this@MeshService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
        }
        return START_STICKY
    }

    /** Mandatory foreground entry for each service start, with location limited to explicit sharing state. */
    private fun startForegroundForCurrentState(notification: android.app.Notification): Boolean =
        synchronized(foregroundStateLock) {
            val decision = currentLocationForegroundDecision()
            val effectiveType = startForegroundSafely(notification, decision.serviceType) ?: return@synchronized false
            isForegroundStarted = true
            effectiveForegroundServiceType = effectiveType
            locationManager.setLocationAccessAllowed(decision.allowsAccessWith(effectiveType))
            true
        }

    /**
     * Promotes, preserves, or drops the location type as consent and platform prerequisites change.
     *
     * A service that already acquired the type while the UI was visible may retain it in the background. This is what
     * lets an explicitly enabled feed resume after a screen-off BLE reconnect without requesting background location.
     */
    private fun reconcileLocationForegroundAccess() {
        val shouldReconcileLocation =
            synchronized(foregroundStateLock) {
                if (!isForegroundStarted) {
                    locationManager.setLocationAccessAllowed(false)
                    return@synchronized false
                }

                val decision = currentLocationForegroundDecision()
                val effectiveType =
                    if (decision.serviceType == effectiveForegroundServiceType) {
                        effectiveForegroundServiceType
                    } else {
                        startForegroundSafely(androidNotifications.getServiceNotification(), decision.serviceType)
                            ?: run {
                                locationManager.setLocationAccessAllowed(false)
                                return@synchronized false
                            }
                    }
                effectiveForegroundServiceType = effectiveType
                locationManager.setLocationAccessAllowed(decision.allowsAccessWith(effectiveType))
                true
            }
        if (shouldReconcileLocation) connectionManager.reconcileLocation()
    }

    private fun currentLocationForegroundDecision(): LocationForegroundDecision = locationForegroundDecision(
        locationRequested = connectionManager.locationSharingRequested.value,
        hasLocationPermission = hasLocationPermission(),
        systemLocationEnabled = isLocationEnabled(),
        appInForeground = processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        currentServiceType = effectiveForegroundServiceType,
    )

    private fun registerLocationReconcileSignals() {
        if (!processLifecycleObserverRegistered) {
            processLifecycle.addObserver(processLifecycleObserver)
            processLifecycleObserverRegistered = true
        }
        if (!locationModeReceiverRegistered) {
            try {
                registerReceiverCompat(
                    locationModeReceiver,
                    IntentFilter().apply {
                        addAction(LocationManager.MODE_CHANGED_ACTION)
                        addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                    },
                )
                locationModeReceiverRegistered = true
            } catch (error: SecurityException) {
                Logger.w(error) { "System location changes cannot be observed; foreground lifecycle remains active" }
            }
        }
        if (!locationPermissionObserverRegistered) {
            try {
                appOpsManager.startWatchingMode(
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    packageName,
                    locationPermissionChangedListener,
                )
                appOpsManager.startWatchingMode(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    packageName,
                    locationPermissionChangedListener,
                )
                locationPermissionObserverRegistered = true
            } catch (error: SecurityException) {
                appOpsManager.stopWatchingMode(locationPermissionChangedListener)
                Logger.w(error) {
                    "Location permission changes cannot be observed; foreground lifecycle remains active"
                }
            }
        }
    }

    private fun unregisterLocationReconcileSignals() {
        if (processLifecycleObserverRegistered) {
            processLifecycle.removeObserver(processLifecycleObserver)
            processLifecycleObserverRegistered = false
        }
        if (locationModeReceiverRegistered) {
            unregisterReceiver(locationModeReceiver)
            locationModeReceiverRegistered = false
        }
        if (locationPermissionObserverRegistered) {
            appOpsManager.stopWatchingMode(locationPermissionChangedListener)
            locationPermissionObserverRegistered = false
        }
    }

    private fun LocationForegroundDecision.allowsAccessWith(effectiveType: Int): Boolean = locationAccessAllowed &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                effectiveType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0
            )

    /**
     * @return the effective service type, including a connected-device fallback, or null when foreground entry fails.
     */
    private fun startForegroundSafely(notification: android.app.Notification, foregroundServiceType: Int): Int? {
        @Suppress("TooGenericExceptionCaught")
        return try {
            ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, foregroundServiceType)
            foregroundServiceType
        } catch (ex: IllegalStateException) {
            Logger.e(ex) { "Foreground service start was not allowed by the OS" }
            null
        } catch (ex: SecurityException) {
            // On Android 14+ starting a location FGS from the background can fail with SecurityException
            // if the app is not in an allowed state. Retry without the location type if that was requested.
            retryForegroundWithoutLocation(ex, notification, foregroundServiceType)
        } catch (ex: IllegalArgumentException) {
            retryForegroundWithoutLocation(ex, notification, foregroundServiceType)
        } catch (ex: Exception) {
            Logger.e(ex) { "Error starting foreground service" }
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun retryForegroundWithoutLocation(
        cause: RuntimeException,
        notification: android.app.Notification,
        requestedType: Int,
    ): Int? {
        val connectedDeviceOnly =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        if (requestedType == connectedDeviceOnly) {
            Logger.e(cause) { "Foreground service start failed with no additive location type to remove" }
            return null
        }

        Logger.w(cause) { "Location foreground type was refused; retrying as connectedDevice" }
        return try {
            ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, connectedDeviceOnly)
            connectedDeviceOnly
        } catch (retryError: Exception) {
            Logger.e(retryError) { "Failed to start foreground service after dropping location type" }
            null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Logger.i { "Mesh service: onTaskRemoved" }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Logger.i { "Destroying mesh service" }
        pendingDeviceSelectionStopJob?.cancel()
        pendingDeviceSelectionStopJob = null
        unregisterLocationReconcileSignals()
        synchronized(foregroundStateLock) {
            isForegroundStarted = false
            effectiveForegroundServiceType = 0
            locationManager.setLocationAccessAllowed(false)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (isServiceInitialized) {
            orchestrator.stop()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    private val binder =
        object : IMeshService.Stub() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun setDeviceAddress(deviceAddr: String?) = toRemoteExceptions {
                Logger.d { "Passing through device change to radio service: ${deviceAddr?.anonymize}" }
                router.actionHandler.handleUpdateLastAddress(deviceAddr)
                radioInterfaceService.setDeviceAddress(deviceAddr)
            }

            override fun subscribeReceiver(packageName: String, receiverName: String) {
                serviceBroadcasts.subscribeReceiver(receiverName, packageName)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun getUpdateStatus(): Int = -4

            @Suppress("OVERRIDE_DEPRECATION")
            override fun startFirmwareUpdate() {
                // No-op: firmware update is handled by the in-app OTA system.
            }

            override fun getMyNodeInfo(): MyNodeInfo? = nodeManager.getMyNodeInfo()

            override fun getMyId(): String = nodeManager.getMyId()

            override fun getPacketId(): Int = commandSender.generatePacketId()

            override fun setOwner(u: MeshUser) = toRemoteExceptions {
                router.actionHandler.handleSetOwner(u, myNodeNum)
            }

            override fun setRemoteOwner(id: Int, destNum: Int, payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetRemoteOwner(id, destNum, payload)
            }

            override fun getRemoteOwner(id: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRemoteOwner(id, destNum)
            }

            override fun send(p: DataPacket) = toRemoteExceptions { router.actionHandler.handleSend(p, myNodeNum) }

            override fun getConfig(): ByteArray = toRemoteExceptions { commandSender.getCachedLocalConfig().encode() }

            override fun setConfig(payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetConfig(payload, myNodeNum)
            }

            override fun setRemoteConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetRemoteConfig(id, num, payload)
            }

            override fun getRemoteConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRemoteConfig(id, destNum, config)
            }

            override fun setModuleConfig(id: Int, num: Int, payload: ByteArray) = toRemoteExceptions {
                router.actionHandler.handleSetModuleConfig(id, num, payload)
            }

            override fun getModuleConfig(id: Int, destNum: Int, config: Int) = toRemoteExceptions {
                router.actionHandler.handleGetModuleConfig(id, destNum, config)
            }

            override fun setRingtone(destNum: Int, ringtone: String) = toRemoteExceptions {
                router.actionHandler.handleSetRingtone(destNum, ringtone)
            }

            override fun getRingtone(id: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRingtone(id, destNum)
            }

            override fun setCannedMessages(destNum: Int, messages: String) = toRemoteExceptions {
                router.actionHandler.handleSetCannedMessages(destNum, messages)
            }

            override fun getCannedMessages(id: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetCannedMessages(id, destNum)
            }

            override fun setChannel(payload: ByteArray?) = toRemoteExceptions {
                router.actionHandler.handleSetChannel(payload, myNodeNum)
            }

            override fun setRemoteChannel(id: Int, num: Int, payload: ByteArray?) = toRemoteExceptions {
                router.actionHandler.handleSetRemoteChannel(id, num, payload)
            }

            override fun getRemoteChannel(id: Int, destNum: Int, index: Int) = toRemoteExceptions {
                router.actionHandler.handleGetRemoteChannel(id, destNum, index)
            }

            override fun beginEditSettings(destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleBeginEditSettings(destNum)
            }

            override fun commitEditSettings(destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleCommitEditSettings(destNum)
            }

            override fun getChannelSet(): ByteArray = toRemoteExceptions {
                commandSender.getCachedChannelSet().encode()
            }

            override fun getNodes(): List<NodeInfo> = nodeManager.getNodes()

            override fun connectionState(): String = serviceRepository.connectionState.value.toString()

            override fun startProvideLocation() {
                // Deprecated Binder calls are only reconciliation hints. The private per-node preference remains the
                // authority, so an external AIDL caller cannot turn location sharing on.
                reconcileLocationForegroundAccess()
            }

            override fun stopProvideLocation() {
                // Likewise, stopping is driven by the preference collector; a caller cannot disable an enabled feed.
                reconcileLocationForegroundAccess()
            }

            override fun removeByNodenum(requestId: Int, nodeNum: Int) = toRemoteExceptions {
                val myNodeNum = nodeManager.myNodeNum.value
                if (myNodeNum != null) {
                    router.actionHandler.handleRemoveByNodenum(nodeNum, requestId, myNodeNum)
                } else {
                    nodeManager.removeByNodenum(nodeNum)
                }
            }

            override fun requestUserInfo(destNum: Int) = toRemoteExceptions {
                if (destNum != myNodeNum) {
                    commandSender.requestUserInfo(destNum)
                }
            }

            override fun requestPosition(destNum: Int, position: Position) = toRemoteExceptions {
                router.actionHandler.handleRequestPosition(destNum, position, myNodeNum)
            }

            override fun setFixedPosition(destNum: Int, position: Position) = toRemoteExceptions {
                commandSender.setFixedPosition(destNum, position)
            }

            override fun requestTraceroute(requestId: Int, destNum: Int) = toRemoteExceptions {
                commandSender.requestTraceroute(requestId, destNum)
            }

            override fun requestNeighborInfo(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestNeighborInfo(requestId, destNum)
            }

            override fun requestShutdown(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestShutdown(requestId, destNum)
            }

            override fun requestReboot(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestReboot(requestId, destNum)
            }

            override fun rebootToDfu(destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRebootToDfu(destNum)
            }

            override fun requestFactoryReset(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestFactoryReset(requestId, destNum)
            }

            override fun requestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) =
                toRemoteExceptions {
                    router.actionHandler.handleRequestNodedbReset(requestId, destNum, preserveFavorites)
                }

            override fun getDeviceConnectionStatus(requestId: Int, destNum: Int) = toRemoteExceptions {
                router.actionHandler.handleGetDeviceConnectionStatus(requestId, destNum)
            }

            override fun requestTelemetry(requestId: Int, destNum: Int, type: Int) = toRemoteExceptions {
                router.actionHandler.handleRequestTelemetry(requestId, destNum, type)
            }

            override fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) =
                toRemoteExceptions {
                    router.actionHandler.handleRequestRebootOta(requestId, destNum, mode, hash)
                }
        }
}

internal enum class MeshServiceStartupDecision {
    KEEP_RUNNING,
    AWAIT_DEVICE,
    STOP,
}

internal fun meshServiceStartupDecision(deviceAddress: String?, graceElapsed: Boolean): MeshServiceStartupDecision {
    val hasSelectedDevice = !deviceAddress.isNullOrBlank() && !deviceAddress.equals("n", ignoreCase = true)
    return when {
        hasSelectedDevice -> MeshServiceStartupDecision.KEEP_RUNNING
        graceElapsed -> MeshServiceStartupDecision.STOP
        else -> MeshServiceStartupDecision.AWAIT_DEVICE
    }
}

private const val DEVICE_SELECTION_STARTUP_GRACE_MS = 15_000L
