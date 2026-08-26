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
package com.ntsocial.meshlink.app

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.app.radio.AndroidEndpointConversationSourceCoordinator
import com.ntsocial.meshlink.core.database.DatabaseManager
import com.ntsocial.meshlink.core.repository.MeshPrefs
import com.ntsocial.meshlink.core.service.NtsocialGatewayEventPublisher
import com.ntsocial.meshlink.core.service.worker.MeshLogCleanupWorker
import com.ntsocial.meshlink.feature.widget.LocalStatsWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.android.ext.android.get
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * The main application class for Meshtastic.
 *
 * This class initializes core application components using Koin for dependency injection.
 */
open class MeshUtilApplication :
    Application(),
    Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AndroidKoinBootstrap.ensureStarted(this)

        // The Provider may be created before Application.onCreate, so Gateway event collection starts only after Koin
        // is fully initialized. Events contain metadata only; external clients re-query the protected Provider.
        get<NtsocialGatewayEventPublisher>().start()
        get<AndroidEndpointConversationSourceCoordinator>().start()

        // Schedule periodic MeshLog cleanup
        scheduleMeshLogCleanup()

        // Generate and publish widget preview for Android 15+ widget picker
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applicationScope.launch {
                suspend fun pushPreview() {
                    try {
                        Logger.i { "Pushing generated widget preview..." }
                        val result =
                            GlanceAppWidgetManager(this@MeshUtilApplication)
                                .setWidgetPreviews(
                                    LocalStatsWidgetReceiver::class,
                                    intSetOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN),
                                )
                        Logger.i { "setWidgetPreviews result: $result" }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Logger.e(e) { "Failed to set widget preview" }
                    }
                }

                pushPreview()

                val widgetStateProvider: com.ntsocial.meshlink.feature.widget.LocalStatsWidgetStateProvider = get()
                try {
                    // Wait for real data for up to 30 seconds before pushing an updated preview
                    withTimeout(30.seconds) {
                        widgetStateProvider.state.first { it.showContent && it.nodeShortName != null }
                    }

                    Logger.i { "Real node data acquired. Pushing updated widget preview." }
                    pushPreview()
                } catch (e: TimeoutCancellationException) {
                    Logger.i(e) { "Timed out waiting for real node data for widget preview." }
                }
            }
        }

        // Initialize DatabaseManager asynchronously with current device address so DAO consumers have an active DB
        applicationScope.launch {
            val dbManager: DatabaseManager = get()
            val meshPrefs: MeshPrefs = get()
            dbManager.init(meshPrefs.deviceAddress.value)
        }
    }

    override fun onTerminate() {
        // Shutdown managers (useful for Robolectric tests)
        get<DatabaseManager>().close()
        applicationScope.cancel()
        super.onTerminate()
        AndroidKoinBootstrap.stop()
    }

    private fun scheduleMeshLogCleanup() {
        val cleanupRequest =
            PeriodicWorkRequestBuilder<MeshLogCleanupWorker>(repeatInterval = 1.hours.toJavaDuration()).build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                MeshLogCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                cleanupRequest,
            )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(get()).build()
}
