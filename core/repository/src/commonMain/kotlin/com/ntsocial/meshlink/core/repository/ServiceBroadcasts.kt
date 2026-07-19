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
package com.ntsocial.meshlink.core.repository

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.Node

/** Interface for broadcasting service-level events to the application. */
interface ServiceBroadcasts {
    /** Subscribes a receiver to mesh broadcasts. */
    fun subscribeReceiver(receiverName: String, packageName: String)

    /** Broadcasts received data to the application. */
    fun broadcastReceivedData(dataPacket: DataPacket)

    /** Broadcasts that the radio connection state has changed. */
    fun broadcastConnection()

    /** Broadcasts that node information has changed. */
    fun broadcastNodeChange(node: Node)

    /** Broadcasts that the status of a message has changed. */
    fun broadcastMessageStatus(packetId: Int, status: MessageStatus)
}
