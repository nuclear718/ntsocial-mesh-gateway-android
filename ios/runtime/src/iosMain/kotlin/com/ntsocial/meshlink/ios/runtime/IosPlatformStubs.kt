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
@file:Suppress("EmptyFunctionBlock", "TooManyFunctions")

package com.ntsocial.meshlink.ios.runtime

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceHardwareJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceLinksJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.FirmwareReleaseJsonDataSource
import com.ntsocial.meshlink.core.model.BootloaderOtaQuirk
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.NetworkDeviceHardware
import com.ntsocial.meshlink.core.model.NetworkDeviceLinksResponse
import com.ntsocial.meshlink.core.model.NetworkFirmwareReleases
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.network.repository.DiscoveredService
import com.ntsocial.meshlink.core.network.repository.MQTTRepository
import com.ntsocial.meshlink.core.network.repository.NetworkMonitor
import com.ntsocial.meshlink.core.network.repository.ServiceDiscovery
import com.ntsocial.meshlink.core.network.service.ApiService
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.DataPair
import com.ntsocial.meshlink.core.repository.FileService
import com.ntsocial.meshlink.core.repository.Location
import com.ntsocial.meshlink.core.repository.LocationRepository
import com.ntsocial.meshlink.core.repository.LocationService
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.Notification
import com.ntsocial.meshlink.core.repository.NotificationManager
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.feature.node.compass.CompassHeadingProvider
import com.ntsocial.meshlink.feature.node.compass.HeadingState
import com.ntsocial.meshlink.feature.node.compass.MagneticFieldProvider
import com.ntsocial.meshlink.feature.node.compass.PhoneLocationProvider
import com.ntsocial.meshlink.feature.node.compass.PhoneLocationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.mqtt.ConnectionState as MqttConnectionState

/** Lifecycle bridge updated from SwiftUI scenePhase; it remains alive while the iOS process is suspended. */
internal class IosProcessLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }

    override val lifecycle: Lifecycle
        get() = registry

    fun setActive(active: Boolean) {
        registry.currentState = if (active) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
    }
}

internal object IosPlatformAnalytics : PlatformAnalytics {
    override fun track(event: String, vararg properties: DataPair) {}

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {}

    override val isPlatformServicesAvailable: Boolean = false
}

internal object IosServiceBroadcasts : ServiceBroadcasts {
    override fun subscribeReceiver(receiverName: String, packageName: String) {}

    override fun broadcastReceivedData(dataPacket: DataPacket) {}

    override fun broadcastConnection() {}

    override fun broadcastNodeChange(node: Node) {}

    override fun broadcastMessageStatus(packetId: Int, status: MessageStatus) {}
}

internal object IosAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() {}
}

internal object IosMeshWorkerManager : MeshWorkerManager {
    override fun enqueueSendMessage(packetId: Int) {}
}

/** Companion notifications are intentionally deferred; parent Gateway wakeups use payload-free Darwin notifications. */
internal object IosNotificationManager : NotificationManager {
    override fun dispatch(notification: Notification) {}

    override fun cancel(id: Int) {}

    override fun cancelAll() {}
}

internal object IosMeshLocationManager : MeshLocationManager {
    override fun start(scope: CoroutineScope, sendPositionFn: (Position) -> Unit) {}

    override fun restart() {}

    override fun setLocationAccessAllowed(allowed: Boolean) {}

    override fun stop() {}
}

internal object IosLocationRepository : LocationRepository {
    override val receivingLocationUpdates: StateFlow<Boolean> = MutableStateFlow(false)

    override fun getLocations(): Flow<Location> = emptyFlow()
}

/** File import/export remains unavailable until the Swift document-picker bridge supplies a security-scoped URL. */
internal object IosFileService : FileService {
    override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean = false

    override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean = false
}

/** One-shot phone location is fail closed until CoreLocation consent and lifecycle ownership are implemented. */
internal object IosLocationService : LocationService {
    override suspend fun getCurrentLocation(): Location? = null
}

internal object IosCompassHeadingProvider : CompassHeadingProvider {
    override fun headingUpdates(): Flow<HeadingState> = flowOf(HeadingState(hasSensor = false))
}

internal object IosPhoneLocationProvider : PhoneLocationProvider {
    override fun locationUpdates(): Flow<PhoneLocationState> =
        flowOf(PhoneLocationState(permissionGranted = false, providerEnabled = false))
}

internal object IosMagneticFieldProvider : MagneticFieldProvider {
    override fun getDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long): Float = 0F
}

internal object IosMqttRepository : MQTTRepository {
    override fun disconnect() {}

    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = emptyFlow()

    override fun publish(topic: String, data: ByteArray, retained: Boolean) {}

    override val connectionState: StateFlow<MqttConnectionState> =
        MutableStateFlow(MqttConnectionState.Disconnected.Idle)
}

internal object IosNetworkMonitor : NetworkMonitor {
    override val networkAvailable: Flow<Boolean> = flowOf(false)
}

internal object IosServiceDiscovery : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> = flowOf(emptyList())
}

internal object IosApiService : ApiService {
    override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = emptyList()

    override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = NetworkDeviceLinksResponse()

    override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = NetworkFirmwareReleases()
}

internal object IosFirmwareReleaseJsonDataSource : FirmwareReleaseJsonDataSource {
    override fun loadFirmwareReleaseFromJsonAsset(): NetworkFirmwareReleases = NetworkFirmwareReleases()
}

internal object IosDeviceHardwareJsonDataSource : DeviceHardwareJsonDataSource {
    override fun loadDeviceHardwareFromJsonAsset(): List<NetworkDeviceHardware> = emptyList()
}

internal object IosDeviceLinksJsonDataSource : DeviceLinksJsonDataSource {
    override fun loadDeviceLinksFromJsonAsset(): NetworkDeviceLinksResponse = NetworkDeviceLinksResponse()
}

internal object IosBootloaderOtaQuirksJsonDataSource : BootloaderOtaQuirksJsonDataSource {
    override fun loadBootloaderOtaQuirksFromJsonAsset(): List<BootloaderOtaQuirk> = emptyList()
}

internal object IosMeshServiceNotifications : MeshServiceNotifications {
    override fun clearNotifications() {}

    override fun initChannels() {}

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {}

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {}

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {}

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {}

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {}

    override fun showNewNodeSeenNotification(node: Node) {}

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) {}

    override fun showClientNotification(clientNotification: ClientNotification) {}

    override fun cancelMessageNotification(contactKey: String) {}

    override fun cancelLowBatteryNotification(node: Node) {}

    override fun clearClientNotification(notification: ClientNotification) {}
}
