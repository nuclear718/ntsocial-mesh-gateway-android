/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.desktop.notification

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.Notification
import com.ntsocial.meshlink.core.repository.NotificationManager
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.desktop_notification_title
import com.ntsocial.meshlink.core.resources.getString
import com.ntsocial.meshlink.core.resources.low_battery_message
import com.ntsocial.meshlink.core.resources.low_battery_title
import com.ntsocial.meshlink.core.resources.new_node_seen
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/**
 * Desktop implementation of [MeshServiceNotifications].
 *
 * Converts mesh-layer notification events into domain [Notification] objects and dispatches them through
 * [NotificationManager], which ultimately surfaces them as Compose Desktop tray notifications.
 *
 * Android-only concepts (notification channels, foreground-service state updates) are intentionally no-ops.
 *
 * Registered manually in `desktopPlatformStubsModule` -- do **not** add `@Single` to avoid double-registration with the
 * `@ComponentScan("com.ntsocial.meshlink.desktop")` in
 * [DesktopDiModule][com.ntsocial.meshlink.desktop.di.DesktopDiModule].
 */
@Suppress("TooManyFunctions")
class DesktopMeshServiceNotifications(private val notificationManager: NotificationManager) : MeshServiceNotifications {
    override fun clearNotifications() {
        notificationManager.cancelAll()
    }

    override fun initChannels() {
        // No-op: desktop has no Android notification channels.
    }

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {
        // No-op: desktop has no foreground service notification.
    }

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = message,
                category = Notification.Category.Message,
                contactKey = contactKey,
                isSilent = isSilent,
                id = contactKey.hashCode(),
            ),
        )
    }

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = message,
                category = Notification.Category.Message,
                contactKey = contactKey,
                isSilent = isSilent,
            ),
        )
    }

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        notificationManager.dispatch(
            Notification(
                title = name,
                message = emoji,
                category = Notification.Category.Message,
                contactKey = contactKey,
                isSilent = isSilent,
            ),
        )
    }

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {
        val notification =
            Notification(title = name, message = alert, category = Notification.Category.Alert, contactKey = contactKey)
        notificationManager.dispatch(notification)
    }

    override fun showNewNodeSeenNotification(node: Node) {
        notificationManager.dispatch(
            Notification(
                title = getString(Res.string.new_node_seen, node.user.short_name),
                message = node.user.long_name,
                category = Notification.Category.NodeEvent,
            ),
        )
    }

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) {
        notificationManager.dispatch(
            Notification(
                title = getString(Res.string.low_battery_title, node.user.short_name),
                message = getString(Res.string.low_battery_message, node.user.long_name, node.batteryLevel ?: 0),
                category = Notification.Category.Battery,
                id = node.num,
            ),
        )
    }

    override fun showClientNotification(clientNotification: ClientNotification) {
        notificationManager.dispatch(
            Notification(
                title = getString(Res.string.desktop_notification_title),
                message = clientNotification.message,
                category = Notification.Category.Alert,
                id = clientNotification.toString().hashCode(),
            ),
        )
    }

    override fun cancelMessageNotification(contactKey: String) {
        notificationManager.cancel(contactKey.hashCode())
    }

    override fun cancelLowBatteryNotification(node: Node) {
        notificationManager.cancel(node.num)
    }

    override fun clearClientNotification(notification: ClientNotification) {
        notificationManager.cancel(notification.toString().hashCode())
    }
}
