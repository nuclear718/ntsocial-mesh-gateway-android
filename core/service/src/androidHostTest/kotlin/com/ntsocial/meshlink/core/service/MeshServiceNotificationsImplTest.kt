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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.resources.R.color
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeshServiceNotificationsImplTest {

    private lateinit var context: Context
    private lateinit var systemNotificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemNotificationManager = context.getSystemService(NotificationManager::class.java)!!
        clearManagedChannels()
    }

    @After
    fun tearDown() {
        clearManagedChannels()
    }

    @Test
    fun `initChannels removes legacy categories and creates canonical channels`() {
        NotificationChannels.LEGACY_CATEGORY_IDS.forEach(::createChannel)

        val notifications =
            MeshServiceNotificationsImpl(
                context = context,
                packetRepository = lazy<PacketRepository> { error("Not used in this test") },
                nodeRepository = lazy<NodeRepository> { error("Not used in this test") },
            )

        notifications.initChannels()

        NotificationChannels.LEGACY_CATEGORY_IDS.forEach { legacyId ->
            assertNull(systemNotificationManager.getNotificationChannel(legacyId))
        }

        val canonicalChannelIds =
            listOf(
                NotificationChannels.SERVICE,
                NotificationChannels.MESSAGES,
                NotificationChannels.BROADCASTS,
                NotificationChannels.WAYPOINTS,
                NotificationChannels.ALERTS,
                NotificationChannels.NEW_NODES,
                NotificationChannels.LOW_BATTERY,
                NotificationChannels.LOW_BATTERY_REMOTE,
                NotificationChannels.CLIENT,
            )

        canonicalChannelIds.forEach { channelId ->
            val channel = assertNotNull(systemNotificationManager.getNotificationChannel(channelId))
            assertEquals(context.getColor(color.ntsocial_notification_green), channel.lightColor)
        }
    }

    private fun createChannel(id: String) {
        systemNotificationManager.createNotificationChannel(
            NotificationChannel(id, id, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun clearManagedChannels() {
        val channelIds =
            NotificationChannels.LEGACY_CATEGORY_IDS +
                listOf(
                    NotificationChannels.SERVICE,
                    NotificationChannels.MESSAGES,
                    NotificationChannels.BROADCASTS,
                    NotificationChannels.WAYPOINTS,
                    NotificationChannels.ALERTS,
                    NotificationChannels.NEW_NODES,
                    NotificationChannels.LOW_BATTERY,
                    NotificationChannels.LOW_BATTERY_REMOTE,
                    NotificationChannels.CLIENT,
                )

        channelIds.forEach { channelId -> systemNotificationManager.deleteNotificationChannel(channelId) }
    }
}
