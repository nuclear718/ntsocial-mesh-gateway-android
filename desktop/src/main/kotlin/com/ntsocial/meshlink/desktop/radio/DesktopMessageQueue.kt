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
package com.ntsocial.meshlink.desktop.radio

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.PacketRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Desktop implementation of [MessageQueue].
 *
 * Unlike Android which uses WorkManager to ensure delivery across app lifecycles, Desktop immediately delegates to the
 * active controller to send the message.
 */
class DesktopMessageQueue(
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
    dispatchers: CoroutineDispatchers,
) : MessageQueue {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun enqueue(packetId: Int) {
        scope.launch {
            if (packetId == 0) return@launch

            // Verify we are connected before attempting to send to avoid unnecessary Exception bubbling
            if (radioController.connectionState.value != ConnectionState.Connected) {
                // In a real desktop environment, we might want a background loop to retry queued messages.
                // For now, it will retry when connection is re-established (handled by
                // MeshConnectionManager.onRadioConfigLoaded).
                return@launch
            }

            val packetData =
                packetRepository.getPacketByPacketId(packetId)
                    ?: return@launch // Packet no longer exists in DB? Do not retry.

            try {
                radioController.sendMessage(packetData)
                packetRepository.updateMessageStatus(packetData, MessageStatus.ENROUTE)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "Failed to send packet ${packetData.id}, re-queuing" }
                packetRepository.updateMessageStatus(packetData, MessageStatus.QUEUED)
            }
        }
    }
}
