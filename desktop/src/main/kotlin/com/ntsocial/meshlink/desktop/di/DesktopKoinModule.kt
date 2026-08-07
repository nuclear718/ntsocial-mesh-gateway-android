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
    "ktlint:standard:no-unused-imports",
) // Koin K2 compiler plugin generates aliased module extensions referenced in desktopModule()

package com.ntsocial.meshlink.desktop.di

// Generated Koin module extensions from core KMP modules
import com.ntsocial.meshlink.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceHardwareJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceLinksJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.FirmwareReleaseJsonDataSource
import com.ntsocial.meshlink.core.model.BootloaderOtaQuirk
import com.ntsocial.meshlink.core.model.NetworkDeviceHardware
import com.ntsocial.meshlink.core.model.NetworkDeviceLinksResponse
import com.ntsocial.meshlink.core.model.NetworkFirmwareReleases
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.network.HttpClientDefaults
import com.ntsocial.meshlink.core.network.KermitHttpLogger
import com.ntsocial.meshlink.core.network.repository.MQTTRepository
import com.ntsocial.meshlink.core.network.service.ApiService
import com.ntsocial.meshlink.core.network.service.ApiServiceImpl
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.LocationRepository
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NotificationManager
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioTransportFactory
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.service.DirectRadioControllerImpl
import com.ntsocial.meshlink.core.service.ServiceRepositoryImpl
import com.ntsocial.meshlink.desktop.DesktopBuildConfig
import com.ntsocial.meshlink.desktop.DesktopNotificationManager
import com.ntsocial.meshlink.desktop.branding.NtsocialWindowsBranding
import com.ntsocial.meshlink.desktop.notification.DesktopMeshServiceNotifications
import com.ntsocial.meshlink.desktop.notification.DesktopOS
import com.ntsocial.meshlink.desktop.notification.LinuxNotificationSender
import com.ntsocial.meshlink.desktop.notification.MacOSNotificationSender
import com.ntsocial.meshlink.desktop.notification.NativeNotificationSender
import com.ntsocial.meshlink.desktop.notification.WindowsNotificationSender
import com.ntsocial.meshlink.desktop.radio.DesktopMessageQueue
import com.ntsocial.meshlink.desktop.radio.DesktopRadioTransportFactory
import com.ntsocial.meshlink.desktop.stub.NoopAppWidgetUpdater
import com.ntsocial.meshlink.desktop.stub.NoopCompassHeadingProvider
import com.ntsocial.meshlink.desktop.stub.NoopLocationRepository
import com.ntsocial.meshlink.desktop.stub.NoopMQTTRepository
import com.ntsocial.meshlink.desktop.stub.NoopMagneticFieldProvider
import com.ntsocial.meshlink.desktop.stub.NoopMeshLocationManager
import com.ntsocial.meshlink.desktop.stub.NoopMeshWorkerManager
import com.ntsocial.meshlink.desktop.stub.NoopPhoneLocationProvider
import com.ntsocial.meshlink.desktop.stub.NoopPlatformAnalytics
import com.ntsocial.meshlink.desktop.stub.NoopServiceBroadcasts
import com.ntsocial.meshlink.feature.node.compass.CompassHeadingProvider
import com.ntsocial.meshlink.feature.node.compass.MagneticFieldProvider
import com.ntsocial.meshlink.feature.node.compass.PhoneLocationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import com.ntsocial.meshlink.core.ble.di.module as coreBleModule
import com.ntsocial.meshlink.core.common.di.module as coreCommonModule
import com.ntsocial.meshlink.core.data.di.module as coreDataModule
import com.ntsocial.meshlink.core.database.di.module as coreDatabaseModule
import com.ntsocial.meshlink.core.datastore.di.module as coreDatastoreModule
import com.ntsocial.meshlink.core.di.di.module as coreDiModule
import com.ntsocial.meshlink.core.domain.di.module as coreDomainModule
import com.ntsocial.meshlink.core.network.di.module as coreNetworkModule
import com.ntsocial.meshlink.core.prefs.di.module as corePrefsModule
import com.ntsocial.meshlink.core.repository.di.module as coreRepositoryModule
import com.ntsocial.meshlink.core.service.di.module as coreServiceModule
import com.ntsocial.meshlink.core.takserver.di.module as coreTakServerModule
import com.ntsocial.meshlink.core.ui.di.module as coreUiModule
import com.ntsocial.meshlink.desktop.di.module as desktopDiModule
import com.ntsocial.meshlink.feature.connections.di.module as featureConnectionsModule
import com.ntsocial.meshlink.feature.firmware.di.module as featureFirmwareModule
import com.ntsocial.meshlink.feature.intro.di.module as featureIntroModule
import com.ntsocial.meshlink.feature.meshcore.di.module as featureMeshCoreModule
import com.ntsocial.meshlink.feature.messaging.di.module as featureMessagingModule
import com.ntsocial.meshlink.feature.node.di.module as featureNodeModule
import com.ntsocial.meshlink.feature.settings.di.module as featureSettingsModule
import com.ntsocial.meshlink.feature.wifiprovision.di.module as featureWifiProvisionModule

/**
 * Koin module for the Desktop target.
 *
 * Includes the generated Koin K2 modules from core KMP libraries (which provide real implementations of prefs, data
 * repositories, managers, datastore data sources, use cases, and ViewModels from `commonMain`).
 *
 * Only truly platform-specific interfaces are stubbed here — things that require Android APIs (BLE/USB transport,
 * notifications, WorkManager, location services, broadcasts, widgets).
 *
 * Platform infrastructure (DataStores, Room database, Lifecycle) is provided by [desktopPlatformModule].
 */
fun desktopModule() = module {
    // Include generated Koin K2 modules from core KMP libraries (commonMain implementations)
    includes(
        com.ntsocial.meshlink.core.di.di.CoreDiModule().coreDiModule(),
        com.ntsocial.meshlink.core.common.di.CoreCommonModule().coreCommonModule(),
        com.ntsocial.meshlink.core.datastore.di.CoreDatastoreModule().coreDatastoreModule(),
        com.ntsocial.meshlink.core.prefs.di.CorePrefsModule().corePrefsModule(),
        com.ntsocial.meshlink.core.database.di.CoreDatabaseModule().coreDatabaseModule(),
        com.ntsocial.meshlink.core.data.di.CoreDataModule().coreDataModule(),
        com.ntsocial.meshlink.core.domain.di.CoreDomainModule().coreDomainModule(),
        com.ntsocial.meshlink.core.repository.di.CoreRepositoryModule().coreRepositoryModule(),
        com.ntsocial.meshlink.core.network.di.CoreNetworkModule().coreNetworkModule(),
        com.ntsocial.meshlink.core.ble.di.CoreBleModule().coreBleModule(),
        com.ntsocial.meshlink.core.ui.di.CoreUiModule().coreUiModule(),
        com.ntsocial.meshlink.core.service.di.CoreServiceModule().coreServiceModule(),
        com.ntsocial.meshlink.core.takserver.di.CoreTakServerModule().coreTakServerModule(),
        com.ntsocial.meshlink.feature.settings.di.FeatureSettingsModule().featureSettingsModule(),
        com.ntsocial.meshlink.feature.node.di.FeatureNodeModule().featureNodeModule(),
        com.ntsocial.meshlink.feature.messaging.di.FeatureMessagingModule().featureMessagingModule(),
        com.ntsocial.meshlink.feature.connections.di.FeatureConnectionsModule().featureConnectionsModule(),
        com.ntsocial.meshlink.feature.meshcore.di.FeatureMeshCoreModule().featureMeshCoreModule(),
        com.ntsocial.meshlink.feature.firmware.di.FeatureFirmwareModule().featureFirmwareModule(),
        com.ntsocial.meshlink.feature.intro.di.FeatureIntroModule().featureIntroModule(),
        com.ntsocial.meshlink.feature.wifiprovision.di.FeatureWifiProvisionModule().featureWifiProvisionModule(),
        com.ntsocial.meshlink.desktop.di.DesktopDiModule().desktopDiModule(),
        desktopPlatformStubsModule(),
    )
}

/**
 * Stubs for truly platform-specific interfaces that have no `commonMain` implementation. These require Android APIs
 * (BLE/USB transport, notifications, WorkManager, location, broadcasts, widgets).
 */
@Suppress("LongMethod")
private fun desktopPlatformStubsModule() = module {
    single<ServiceRepository> { ServiceRepositoryImpl() }
    single<RadioTransportFactory> {
        DesktopRadioTransportFactory(
            dispatchers = get(),
            scanner = get(),
            bluetoothRepository = get(),
            connectionFactory = get(),
        )
    }
    single<RadioController> {
        DirectRadioControllerImpl(
            serviceRepository = get(),
            nodeRepository = get(),
            commandSender = get(),
            router = get(),
            nodeManager = get(),
            radioInterfaceService = get(),
            locationManager = get(),
            messageProcessor = get(),
            packetHandler = get(),
            sessionManager = get(),
        )
    }
    single<NativeNotificationSender> {
        when (DesktopOS.current()) {
            DesktopOS.Linux -> LinuxNotificationSender()
            DesktopOS.MacOS -> MacOSNotificationSender()
            DesktopOS.Windows -> WindowsNotificationSender(appName = NtsocialWindowsBranding.notificationAppId)
        }
    }
    single { DesktopNotificationManager(prefs = get(), nativeSender = get()) }
    single<NotificationManager> { get<DesktopNotificationManager>() }
    single<MeshServiceNotifications> { DesktopMeshServiceNotifications(notificationManager = get()) }
    single<PlatformAnalytics> { NoopPlatformAnalytics() }
    single<ServiceBroadcasts> { NoopServiceBroadcasts() }
    single<AppWidgetUpdater> { NoopAppWidgetUpdater() }
    single<MeshWorkerManager> { NoopMeshWorkerManager() }
    single<MessageQueue> { DesktopMessageQueue(packetRepository = get(), radioController = get(), dispatchers = get()) }
    single<MeshLocationManager> { NoopMeshLocationManager() }
    single<LocationRepository> { NoopLocationRepository() }
    single<MQTTRepository> { NoopMQTTRepository() }
    single<CompassHeadingProvider> { NoopCompassHeadingProvider() }
    single<PhoneLocationProvider> { NoopPhoneLocationProvider() }
    single<MagneticFieldProvider> { NoopMagneticFieldProvider() }

    // Desktop uses the real ApiService implementation (no flavor stub needed)
    single<ApiService> { ApiServiceImpl(client = get()) }

    // Ktor HttpClient for JVM/Desktop (equivalent of CoreNetworkAndroidModule on Android)
    single<HttpClient> {
        HttpClient(Java) {
            install(ContentNegotiation) { json(get<Json>()) }
            install(DefaultRequest) { url(HttpClientDefaults.API_BASE_URL) }
            install(HttpTimeout) {
                requestTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
                connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
                socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = HttpClientDefaults.MAX_RETRIES)
                exponentialDelay()
            }
            if (DesktopBuildConfig.IS_DEBUG) {
                install(Logging) {
                    logger = KermitHttpLogger
                    level = LogLevel.INFO
                }
            }
        }
    }

    // Desktop stubs for data sources that load from Android assets on mobile
    single<FirmwareReleaseJsonDataSource> {
        object : FirmwareReleaseJsonDataSource {
            override fun loadFirmwareReleaseFromJsonAsset() = NetworkFirmwareReleases()
        }
    }
    single<DeviceHardwareJsonDataSource> {
        object : DeviceHardwareJsonDataSource {
            override fun loadDeviceHardwareFromJsonAsset(): List<NetworkDeviceHardware> = emptyList()
        }
    }
    single<DeviceLinksJsonDataSource> {
        object : DeviceLinksJsonDataSource {
            override fun loadDeviceLinksFromJsonAsset(): NetworkDeviceLinksResponse = NetworkDeviceLinksResponse()
        }
    }
    single<BootloaderOtaQuirksJsonDataSource> {
        object : BootloaderOtaQuirksJsonDataSource {
            override fun loadBootloaderOtaQuirksFromJsonAsset(): List<BootloaderOtaQuirk> = emptyList()
        }
    }
}
