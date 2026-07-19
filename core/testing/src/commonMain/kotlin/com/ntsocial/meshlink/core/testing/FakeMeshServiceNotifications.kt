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
package com.ntsocial.meshlink.core.testing

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/** A test double for [MeshServiceNotifications] that provides a no-op implementation. */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
class FakeMeshServiceNotifications : MeshServiceNotifications {
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
