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
@file:Suppress("ktlint:standard:no-unused-imports")

package com.ntsocial.meshlink.ios.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ntsocial.meshlink.core.ble.BleConnectionFactory
import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.BuildConfigProvider
import com.ntsocial.meshlink.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceHardwareJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceLinksJsonDataSource
import com.ntsocial.meshlink.core.data.datasource.FirmwareReleaseJsonDataSource
import com.ntsocial.meshlink.core.datastore.RadioScopedDataStoreFactory
import com.ntsocial.meshlink.core.datastore.di.DATASTORE_SCOPE
import com.ntsocial.meshlink.core.datastore.serializer.ChannelSetSerializer
import com.ntsocial.meshlink.core.datastore.serializer.LocalConfigSerializer
import com.ntsocial.meshlink.core.datastore.serializer.LocalStatsSerializer
import com.ntsocial.meshlink.core.datastore.serializer.ModuleConfigSerializer
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.network.repository.MQTTRepository
import com.ntsocial.meshlink.core.network.repository.NetworkMonitor
import com.ntsocial.meshlink.core.network.repository.ServiceDiscovery
import com.ntsocial.meshlink.core.network.service.ApiService
import com.ntsocial.meshlink.core.radiofleet.DefaultRadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSessionFactory
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.conversation.DefaultFleetChannelsRepository
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointAppearanceStore
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSourceRegistry
import com.ntsocial.meshlink.core.radiofleet.conversation.FleetChannelsRepository
import com.ntsocial.meshlink.core.radiofleet.conversation.MutableEndpointConversationSourceRegistry
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.FileService
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.LocationRepository
import com.ntsocial.meshlink.core.repository.LocationService
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
import com.ntsocial.meshlink.feature.node.compass.CompassHeadingProvider
import com.ntsocial.meshlink.feature.node.compass.MagneticFieldProvider
import com.ntsocial.meshlink.feature.node.compass.PhoneLocationProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
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
import com.ntsocial.meshlink.feature.connections.di.module as featureConnectionsModule
import com.ntsocial.meshlink.feature.meshcore.di.module as featureMeshCoreModule
import com.ntsocial.meshlink.feature.messaging.di.module as featureMessagingModule
import com.ntsocial.meshlink.feature.node.di.module as featureNodeModule
import com.ntsocial.meshlink.feature.settings.di.module as featureSettingsModule
import com.ntsocial.meshlink.feature.wifiprovision.di.module as featureWifiProvisionModule

internal fun iosCoreModule() = module {
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
        com.ntsocial.meshlink.feature.wifiprovision.di.FeatureWifiProvisionModule().featureWifiProvisionModule(),
        iosPlatformModule(),
        iosRadioEndpointKoinModule,
    )
}

@Suppress("LongMethod")
private fun iosPlatformModule() = module {
    includes(iosPreferencesDataStoreModule(), iosProtoDataStoreModule())

    single { IosProcessLifecycleOwner() }
    single(named("ProcessLifecycle")) { get<IosProcessLifecycleOwner>().lifecycle }
    single<BuildConfigProvider> { IosBuildConfigProvider }
    single<ServiceRepository> { ServiceRepositoryImpl() }
    single<RadioTransportFactory> {
        IosRadioTransportFactory(
            scanner = get<BleScanner>(),
            bluetoothRepository = get<BluetoothRepository>(),
            connectionFactory = get<BleConnectionFactory>(),
            dispatchers = get(),
        )
    }
    single<RadioScopedDataStoreFactory> { IosRadioScopedDataStoreFactory(get()) }
    single { IosRadioEndpointScopeRegistry() }
    single<RadioEndpointSessionFactory> {
        IosRadioEndpointSessionFactory(
            koin = getKoin(),
            databaseCatalog = get(),
            scopedDataStoreFactory = get(),
            meshPrefs = get(),
            dispatchers = get(),
            scopeRegistry = get(),
            primaryRadio = get(),
            primaryServiceRepository = get(),
        )
    }
    single<RadioFleetManager> {
        DefaultRadioFleetManager(
            endpointStore = get(),
            sessionFactory = get(),
            scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatchers>().default),
        )
    }
    single { IosEndpointConversationSourceRegistry() }
    single<EndpointConversationSourceRegistry> { get<IosEndpointConversationSourceRegistry>() }
    single<MutableEndpointConversationSourceRegistry> { get<IosEndpointConversationSourceRegistry>() }
    single<FleetChannelsRepository> {
        DefaultFleetChannelsRepository(
            fleetManager = get(),
            sourceRegistry = get(),
            appearanceStore = get<EndpointAppearanceStore>(),
            scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatchers>().default),
        )
    }
    single {
        IosEndpointConversationSourceCoordinator(
            fleetManager = get(),
            scopeRegistry = get(),
            sourceRegistry = get(),
            appearanceStore = get(),
            rootRadioConfigRepository = get(),
            rootPacketRepository = get(),
            dispatchers = get(),
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
    single {
        IosDurableMessageQueue(
            packetRepository = get(),
            radioController = get(),
            commandSender = get(),
            radioConfigRepository = get(),
            radioInterfaceService = get(),
            gatewayIngressSessionGate = get<GatewayIngressSessionGate>(),
            dispatchers = get(),
        )
    }
    single<MessageQueue> { get<IosDurableMessageQueue>() }
    single<MeshServiceNotifications> { IosMeshServiceNotifications }
    single<PlatformAnalytics> { IosPlatformAnalytics }
    single<ServiceBroadcasts> { IosServiceBroadcasts }
    single<AppWidgetUpdater> { IosAppWidgetUpdater }
    single<MeshWorkerManager> { IosMeshWorkerManager }
    single<NotificationManager> { IosNotificationManager }
    single<MeshLocationManager> { IosMeshLocationManager }
    single<LocationRepository> { IosLocationRepository }
    single<LocationService> { IosLocationService }
    single<FileService> { IosFileService }
    single<MQTTRepository> { IosMqttRepository }
    single<CompassHeadingProvider> { IosCompassHeadingProvider }
    single<PhoneLocationProvider> { IosPhoneLocationProvider }
    single<MagneticFieldProvider> { IosMagneticFieldProvider }
    single<NetworkMonitor> { IosNetworkMonitor }
    single<ServiceDiscovery> { IosServiceDiscovery }
    single<ApiService> { IosApiService }
    single<FirmwareReleaseJsonDataSource> { IosFirmwareReleaseJsonDataSource }
    single<DeviceHardwareJsonDataSource> { IosDeviceHardwareJsonDataSource }
    single<DeviceLinksJsonDataSource> { IosDeviceLinksJsonDataSource }
    single<BootloaderOtaQuirksJsonDataSource> { IosBootloaderOtaQuirksJsonDataSource }
}

private fun iosPreferencesDataStoreModule() = module {
    fun Scope.store(name: String): DataStore<Preferences> =
        createPreferencesDataStore(name, get(named(DATASTORE_SCOPE)))

    single<DataStore<Preferences>>(named("HomoglyphEncodingDataStore")) { store("homoglyph_encoding") }
    single<DataStore<Preferences>>(named("AppDataStore")) { store("app") }
    single<DataStore<Preferences>>(named("CustomEmojiDataStore")) { store("custom_emoji") }
    single<DataStore<Preferences>>(named("MapConsentDataStore")) { store("map_consent") }
    single<DataStore<Preferences>>(named("MeshDataStore")) { store("mesh") }
    single<DataStore<Preferences>>(named("RadioDataStore")) { store("radio") }
    single<DataStore<Preferences>>(named("UiDataStore")) { store("ui") }
    single<DataStore<Preferences>>(named("MeshLogDataStore")) { store("meshlog") }
    single<DataStore<Preferences>>(named("FilterDataStore")) { store("filter") }
    single<DataStore<Preferences>>(named("CorePreferencesDataStore")) { store("core_preferences") }
}

private fun iosProtoDataStoreModule() = module {
    single<DataStore<LocalConfig>>(named("CoreLocalConfigDataStore")) {
        createProtoDataStore("local_config.pb", LocalConfigSerializer, LocalConfig(), get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<LocalModuleConfig>>(named("CoreModuleConfigDataStore")) {
        createProtoDataStore(
            "module_config.pb",
            ModuleConfigSerializer,
            LocalModuleConfig(),
            get(named(DATASTORE_SCOPE)),
        )
    }
    single<DataStore<ChannelSet>>(named("CoreChannelSetDataStore")) {
        createProtoDataStore("channel_set.pb", ChannelSetSerializer, ChannelSet(), get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<LocalStats>>(named("CoreLocalStatsDataStore")) {
        createProtoDataStore("local_stats.pb", LocalStatsSerializer, LocalStats(), get(named(DATASTORE_SCOPE)))
    }
}

private fun createPreferencesDataStore(name: String, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        scope = scope,
        produceFile = { (iosDataStoreDirectory() + "/$name.preferences_pb").toPath() },
    )

private fun <T> createProtoDataStore(
    name: String,
    serializer: androidx.datastore.core.okio.OkioSerializer<T>,
    defaultValue: T,
    scope: CoroutineScope,
): DataStore<T> = DataStoreFactory.create(
    storage =
    OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = serializer,
        producePath = { (iosDataStoreDirectory() + "/$name").toPath() },
    ),
    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { defaultValue }),
    scope = scope,
)

@OptIn(ExperimentalForeignApi::class)
internal fun iosApplicationSupportDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val base = requireNotNull(url?.path)
    val directory = "$base/NTsocialMeshLink"
    NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    return directory
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosDataStoreDirectory(): String {
    val directory = iosApplicationSupportDirectory() + "/datastore"
    NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    return directory
}

private object IosBuildConfigProvider : BuildConfigProvider {
    override val isDebug: Boolean = false
    override val applicationId: String = NSBundle.mainBundle.bundleIdentifier ?: "com.ntsocial.meshlink.ios"
    override val versionCode: Int =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 1
    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0.0"
    override val absoluteMinFwVersion: String = "2.3.15"
    override val minFwVersion: String = "2.5.14"
}
