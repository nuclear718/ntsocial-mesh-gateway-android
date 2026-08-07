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
package com.ntsocial.meshlink.core.data.manager

import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.MeshConfigHandler
import com.ntsocial.meshlink.core.repository.NodeManager
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock

class RadioIngressWorkTrackerTest {

    @Test
    fun `old admin passkey finishes before teardown clear and cannot repopulate session`() = runTest {
        val tracker = RadioIngressWorkTracker()
        val sessionManager = SessionManagerImpl(Clock.System)
        val adminHandler =
            AdminPacketHandlerImpl(
                nodeManager = mock<NodeManager>(MockMode.autofill),
                configHandler = lazy { mock<MeshConfigHandler>(MockMode.autofill) },
                configFlowManager = lazy { mock<MeshConfigFlowManager>(MockMode.autofill) },
                sessionManager = sessionManager,
            )
        val handlerEntered = CompletableDeferred<Unit>()
        val allowAdminDecode = CompletableDeferred<Unit>()
        val passkey = byteArrayOf(1, 2, 3, 4).toByteString()
        backgroundScope.launch {
            checkNotNull(tracker.enterHandler())
            handlerEntered.complete(Unit)
            try {
                allowAdminDecode.await()
                adminHandler.handleAdminMessage(adminPacket(REMOTE_NODE, passkey), LOCAL_NODE)
            } finally {
                tracker.exitHandler()
            }
        }
        handlerEntered.await()

        val quiesce = async { tracker.pauseAndAwaitRetiredWork() }
        runCurrent()
        assertFalse(quiesce.isCompleted)

        allowAdminDecode.complete(Unit)
        quiesce.await()
        assertEquals(passkey, sessionManager.getPasskey(REMOTE_NODE))

        sessionManager.clearAll()
        assertEquals(ByteString.EMPTY, sessionManager.getPasskey(REMOTE_NODE))
    }

    private fun adminPacket(from: Int, passkey: ByteString): MeshPacket {
        val payload = AdminMessage.ADAPTER.encode(AdminMessage(session_passkey = passkey)).toByteString()
        return MeshPacket(from = from, decoded = Data(portnum = PortNum.ADMIN_APP, payload = payload))
    }

    private companion object {
        const val LOCAL_NODE = 1
        const val REMOTE_NODE = 2
    }
}
