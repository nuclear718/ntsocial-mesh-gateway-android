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
package com.ntsocial.meshlink.core.service.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.SERVICE_NOTIFY_ID
import com.ntsocial.meshlink.core.resources.R.color
import com.ntsocial.meshlink.core.resources.R.drawable
import com.ntsocial.meshlink.core.service.MeshService
import com.ntsocial.meshlink.core.service.startService
import org.koin.android.annotation.KoinWorker

/**
 * A worker whose sole purpose is to start the MeshService from the background. This is used as a fallback when
 * `startForegroundService` is blocked by Android 14+ restrictions. It runs as an Expedited worker to gain temporary
 * foreground start privileges.
 */
@KoinWorker
class ServiceKeepAliveWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val serviceNotifications: MeshServiceNotifications,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        // We use the same notification channel as the main service notification
        // to minimize user disruption.
        // On Android 12+, we need to provide a foreground info for expedited work.
        val notification = createNotification()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SERVICE_NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            ForegroundInfo(SERVICE_NOTIFY_ID, notification)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        Logger.i { "ServiceKeepAliveWorker: Attempting to start MeshService" }
        return try {
            MeshService.startService(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Logger.e(e) { "ServiceKeepAliveWorker failed to start service" }
            Result.failure()
        }
    }

    private fun createNotification(): Notification {
        // We ensure channels are created
        serviceNotifications.initChannels()

        // We create a generic "Resuming" notification.
        // We use "my_service" which matches NotificationType.ServiceState.channelId in MeshServiceNotificationsImpl

        return NotificationCompat.Builder(applicationContext, "my_service")
            .setSmallIcon(drawable.ntsocial_ic_notification)
            .setColor(applicationContext.getColor(color.ntsocial_notification_green))
            .setContentTitle("Resuming Mesh Service")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
