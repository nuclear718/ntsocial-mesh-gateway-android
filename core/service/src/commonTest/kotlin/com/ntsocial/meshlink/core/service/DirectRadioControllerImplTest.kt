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

import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshActionHandler
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshMessageProcessor
import com.ntsocial.meshlink.core.repository.MeshRouter
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class DirectRadioControllerImplTest {
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val nodeRepository = mock<NodeRepository>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val router = mock<MeshRouter>(MockMode.autofill)
    private val actionHandler = mock<MeshActionHandler>(MockMode.autofill)
    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
    private val locationManager = mock<MeshLocationManager>(MockMode.autofill)
    private val messageProcessor = mock<MeshMessageProcessor>(MockMode.autofill)
    private val packetHandler = mock<PacketHandler>(MockMode.autofill)
    private val sessionManager = mock<SessionManager>(MockMode.autofill)

    @Test
    fun `awaited selection starts replacement transport only after database switch completes`() = runTest {
        val databaseSwitchGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        every { router.actionHandler } returns actionHandler
        everySuspend { messageProcessor.quiesceIngress() } calls { events += "quiesce-old-ingress" }
        everySuspend { radioInterfaceService.disconnect() } calls { events += "disconnect-old" }
        everySuspend { packetHandler.stopPacketQueueAndAwait() } calls { events += "clear-old-packet-queue" }
        every { sessionManager.clearAll() } calls { events += "clear-old-admin-sessions" }
        every { radioInterfaceService.resetReceivedBuffer() } calls { events += "drop-buffered-old-ingress" }
        every { messageProcessor.resumeIngress() } calls { events += "resume-new-ingress" }
        everySuspend { actionHandler.handleUpdateLastAddressAndAwait(RADIO_ADDRESS) } calls
            {
                databaseSwitchGate.await()
                events += "database-ready"
                true
            }
        every { radioInterfaceService.setDeviceAddress(RADIO_ADDRESS) } calls
            {
                events += "transport-start"
                true
            }
        val controller = createController()

        val selection = async { controller.setDeviceAddressAndAwait(RADIO_ADDRESS) }
        runCurrent()
        verify(mode = VerifyMode.not) { radioInterfaceService.setDeviceAddress(any()) }
        assertEquals(
            listOf(
                "quiesce-old-ingress",
                "disconnect-old",
                "clear-old-packet-queue",
                "clear-old-admin-sessions",
                "drop-buffered-old-ingress",
            ),
            events,
        )

        databaseSwitchGate.complete(Unit)
        selection.await()

        assertEquals(
            listOf(
                "quiesce-old-ingress",
                "disconnect-old",
                "clear-old-packet-queue",
                "clear-old-admin-sessions",
                "drop-buffered-old-ingress",
                "database-ready",
                "resume-new-ingress",
                "transport-start",
            ),
            events,
        )
    }

    @Test
    fun `superseded awaited selection never starts a transport`() = runTest {
        every { router.actionHandler } returns actionHandler
        everySuspend { radioInterfaceService.disconnect() } returns Unit
        everySuspend { packetHandler.stopPacketQueueAndAwait() } returns Unit
        every { radioInterfaceService.resetReceivedBuffer() } returns Unit
        everySuspend { messageProcessor.quiesceIngress() } returns Unit
        everySuspend { actionHandler.handleUpdateLastAddressAndAwait(RADIO_ADDRESS) } returns false
        val controller = createController()

        controller.setDeviceAddressAndAwait(RADIO_ADDRESS)

        verify(mode = VerifyMode.not) { radioInterfaceService.setDeviceAddress(any()) }
        verify(mode = VerifyMode.not) { messageProcessor.resumeIngress() }
    }

    @Test
    fun `database transition failure stays disconnected and never starts replacement transport`() = runTest {
        every { router.actionHandler } returns actionHandler
        everySuspend { radioInterfaceService.disconnect() } returns Unit
        everySuspend { packetHandler.stopPacketQueueAndAwait() } returns Unit
        every { radioInterfaceService.resetReceivedBuffer() } returns Unit
        everySuspend { messageProcessor.quiesceIngress() } returns Unit
        everySuspend { actionHandler.handleUpdateLastAddressAndAwait(RADIO_ADDRESS) } calls
            {
                throw IllegalStateException("database switch failed")
            }
        val controller = createController()

        assertFailsWith<IllegalStateException> { controller.setDeviceAddressAndAwait(RADIO_ADDRESS) }

        verify(mode = VerifyMode.not) { radioInterfaceService.setDeviceAddress(any()) }
        verify(mode = VerifyMode.not) { messageProcessor.resumeIngress() }
    }

    private fun createController(): DirectRadioControllerImpl = DirectRadioControllerImpl(
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        commandSender = commandSender,
        router = router,
        nodeManager = nodeManager,
        radioInterfaceService = radioInterfaceService,
        locationManager = locationManager,
        messageProcessor = messageProcessor,
        packetHandler = packetHandler,
        sessionManager = sessionManager,
    )

    private companion object {
        const val RADIO_ADDRESS = "xAA:BB:CC:DD:EE:FF"
    }
}
