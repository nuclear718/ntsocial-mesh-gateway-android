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
package com.ntsocial.meshlink.app.di

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.ntsocial.meshlink.core.ble.di.CoreBleAndroidModule
import com.ntsocial.meshlink.core.ble.di.CoreBleModule
import com.ntsocial.meshlink.core.common.BuildConfigProvider
import com.ntsocial.meshlink.core.common.di.CoreCommonModule
import com.ntsocial.meshlink.core.data.di.CoreDataAndroidModule
import com.ntsocial.meshlink.core.data.di.CoreDataModule
import com.ntsocial.meshlink.core.database.di.CoreDatabaseAndroidModule
import com.ntsocial.meshlink.core.database.di.CoreDatabaseModule
import com.ntsocial.meshlink.core.datastore.di.CoreDatastoreAndroidModule
import com.ntsocial.meshlink.core.datastore.di.CoreDatastoreModule
import com.ntsocial.meshlink.core.network.di.CoreNetworkAndroidModule
import com.ntsocial.meshlink.core.network.di.CoreNetworkModule
import com.ntsocial.meshlink.core.network.repository.ProbeTableProvider
import com.ntsocial.meshlink.core.prefs.di.CorePrefsAndroidModule
import com.ntsocial.meshlink.core.prefs.di.CorePrefsModule
import com.ntsocial.meshlink.core.radiofleet.DefaultRadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSessionFactory
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointStore
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.service.di.CoreServiceAndroidModule
import com.ntsocial.meshlink.core.service.di.CoreServiceModule
import com.ntsocial.meshlink.core.takserver.di.CoreTakServerModule
import com.ntsocial.meshlink.core.ui.di.CoreUiModule
import com.ntsocial.meshlink.feature.connections.di.FeatureConnectionsModule
import com.ntsocial.meshlink.feature.firmware.di.FeatureFirmwareModule
import com.ntsocial.meshlink.feature.intro.di.FeatureIntroModule
import com.ntsocial.meshlink.feature.meshcore.di.FeatureMeshCoreModule
import com.ntsocial.meshlink.feature.messaging.di.FeatureMessagingModule
import com.ntsocial.meshlink.feature.node.di.FeatureNodeModule
import com.ntsocial.meshlink.feature.settings.di.FeatureSettingsModule
import com.ntsocial.meshlink.feature.widget.di.FeatureWidgetModule
import com.ntsocial.meshlink.feature.wifiprovision.di.FeatureWifiProvisionModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Module(
    includes =
    [
        com.ntsocial.meshlink.app.MainKoinModule::class,
        com.ntsocial.meshlink.core.di.di.CoreDiModule::class,
        CoreCommonModule::class,
        CoreBleModule::class,
        CoreBleAndroidModule::class,
        CoreDataModule::class,
        CoreDataAndroidModule::class,
        com.ntsocial.meshlink.core.domain.di.CoreDomainModule::class,
        CoreDatabaseModule::class,
        CoreDatabaseAndroidModule::class,
        com.ntsocial.meshlink.core.repository.di.CoreRepositoryModule::class,
        CoreDatastoreModule::class,
        CoreDatastoreAndroidModule::class,
        CorePrefsModule::class,
        CorePrefsAndroidModule::class,
        CoreServiceModule::class,
        CoreServiceAndroidModule::class,
        CoreNetworkModule::class,
        CoreNetworkAndroidModule::class,
        CoreTakServerModule::class,
        CoreUiModule::class,
        FeatureNodeModule::class,
        FeatureMessagingModule::class,
        FeatureConnectionsModule::class,
        FeatureMeshCoreModule::class,
        FeatureSettingsModule::class,
        FeatureFirmwareModule::class,
        FeatureIntroModule::class,
        FeatureWidgetModule::class,
        FeatureWifiProvisionModule::class,
        NetworkModule::class,
        FlavorModule::class,
    ],
)
class AppKoinModule {
    @Single
    @Named("ProcessLifecycle")
    fun provideProcessLifecycle(): Lifecycle = ProcessLifecycleOwner.get().lifecycle

    @Single
    fun provideBuildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val isDebug: Boolean = com.ntsocial.meshlink.app.BuildConfig.DEBUG
        override val applicationId: String = com.ntsocial.meshlink.app.BuildConfig.APPLICATION_ID
        override val versionCode: Int = com.ntsocial.meshlink.app.BuildConfig.VERSION_CODE
        override val versionName: String = com.ntsocial.meshlink.app.BuildConfig.VERSION_NAME
        override val absoluteMinFwVersion: String = com.ntsocial.meshlink.app.BuildConfig.ABS_MIN_FW_VERSION
        override val minFwVersion: String = com.ntsocial.meshlink.app.BuildConfig.MIN_FW_VERSION
    }

    @Single fun provideWorkManager(context: Application): WorkManager = WorkManager.getInstance(context)

    @Single
    fun provideUsbManager(application: Application): UsbManager? =
        application.getSystemService(Context.USB_SERVICE) as UsbManager?

    @Single fun provideProbeTable(provider: ProbeTableProvider): ProbeTable = provider.get()

    @Single fun provideUsbSerialProber(probeTable: ProbeTable): UsbSerialProber = UsbSerialProber(probeTable)

    @Single
    fun provideRadioFleetManager(
        endpointStore: RadioEndpointStore,
        sessionFactory: RadioEndpointSessionFactory,
        dispatchers: com.ntsocial.meshlink.core.di.CoroutineDispatchers,
    ): RadioFleetManager = DefaultRadioFleetManager(
        endpointStore = endpointStore,
        sessionFactory = sessionFactory,
        scope = CoroutineScope(SupervisorJob() + dispatchers.default),
    )
}
