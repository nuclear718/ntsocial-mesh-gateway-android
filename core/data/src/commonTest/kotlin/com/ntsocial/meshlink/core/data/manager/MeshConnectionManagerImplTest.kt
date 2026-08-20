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

import com.ntsocial.meshlink.core.data.ntsocial.NtsocialChannelProvisionResult
import com.ntsocial.meshlink.core.data.ntsocial.NtsocialChannelProvisioner
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.PreciseLocationChannelSetPlanner
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelWriteOrder
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.HistoryManager
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.MqttManager
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.PreciseLocationAdmission
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import com.ntsocial.meshlink.core.repository.UiPrefs
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.TestDataFactory
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MeshConnectionManagerImplTest {
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val serviceBroadcasts = mock<ServiceBroadcasts>(MockMode.autofill)
    private val serviceNotifications = mock<MeshServiceNotifications>(MockMode.autofill)
    private val uiPrefs = mock<UiPrefs>(MockMode.autofill)
    private val packetHandler = mock<PacketHandler>(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val locationManager = mock<MeshLocationManager>(MockMode.autofill)
    private val mqttManager = mock<MqttManager>(MockMode.autofill)
    private val historyManager = mock<HistoryManager>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val sessionManager = mock<SessionManager>(MockMode.autofill)
    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val analytics = mock<PlatformAnalytics>(MockMode.autofill)
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val workerManager = mock<MeshWorkerManager>(MockMode.autofill)
    private val appWidgetUpdater = mock<AppWidgetUpdater>(MockMode.autofill)
    private val ntsocialChannelProvisioner = mock<NtsocialChannelProvisioner>(MockMode.autofill)
    private val ntsocialGatewayRepository = mock<NtsocialGatewayRepository>(MockMode.autofill)
    private val channelReliabilityManager = mock<ChannelReliabilityManager>(MockMode.autofill)

    private val dataPacket = DataPacket(id = 456, time = 0L, to = "0", from = "0", bytes = null, dataType = 0)

    private val radioConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val radioSessionState =
        MutableStateFlow(
            RadioSessionState(
                epoch = SESSION_EPOCH,
                selectedDeviceAddress = RADIO_ADDRESS,
                activeDeviceAddress = RADIO_ADDRESS,
                transportConnectionState = ConnectionState.Connected,
                configured = false,
            ),
        )
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val channelSetFlow = MutableStateFlow(ChannelSet())
    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig())
    private val nodeLocationPrefs = mutableMapOf<Int, MutableStateFlow<Boolean>>()
    private val preciseLocationAdmissions = mutableMapOf<Int, MutableStateFlow<PreciseLocationAdmission>>()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var manager: MeshConnectionManagerImpl

    @BeforeTest
    fun setUp() {
        radioSessionState.value =
            RadioSessionState(
                epoch = SESSION_EPOCH,
                selectedDeviceAddress = RADIO_ADDRESS,
                activeDeviceAddress = RADIO_ADDRESS,
                transportConnectionState = ConnectionState.Connected,
                configured = false,
            )
        every { radioInterfaceService.connectionState } returns radioConnectionState
        every { radioInterfaceService.radioSessionState } returns radioSessionState
        every { radioInterfaceService.markCurrentSessionConfigured(any()) } calls
            { call ->
                val current = radioSessionState.value
                val accepted =
                    call.arg<Long>(0) == current.epoch &&
                        current.selectedDeviceAddress == current.activeDeviceAddress &&
                        current.transportConnectionState == ConnectionState.Connected
                if (accepted) radioSessionState.value = current.copy(configured = true)
                accepted
            }
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
        every { radioConfigRepository.channelSetFlow } returns channelSetFlow
        every { uiPrefs.shouldProvideNodeLocation(any()) } calls
            { call ->
                nodeLocationPrefs.getOrPut(call.arg(0)) { MutableStateFlow(false) }
            }
        every { uiPrefs.preciseLocationChannelIndex(any()) } calls
            { call ->
                preciseLocationAdmissions
                    .getOrPut(call.arg(0)) { MutableStateFlow(PreciseLocationAdmission()) }
                    .let { admission -> MutableStateFlow(admission.value.channelIndex) }
            }
        every { uiPrefs.preciseLocationAdmission(any()) } calls
            { call ->
                preciseLocationAdmissions.getOrPut(call.arg(0)) { MutableStateFlow(PreciseLocationAdmission()) }
            }
        everySuspend { uiPrefs.readPreciseLocationAdmission(any()) } calls
            { call ->
                preciseLocationAdmissions.getOrPut(call.arg(0)) { MutableStateFlow(PreciseLocationAdmission()) }.value
            }
        everySuspend { uiPrefs.setPreciseLocationSharing(any(), any(), any(), any(), any()) } calls
            { call ->
                val nodeNum = call.arg<Int>(0)
                val provide = call.arg<Boolean>(1)
                val channelIndex = call.arg<Int>(2)
                val channelIdentity = call.arg<String>(3)
                val cleanupPending = call.arg<Boolean>(4)
                preciseLocationAdmissions.getOrPut(nodeNum) { MutableStateFlow(PreciseLocationAdmission()) }.value =
                    PreciseLocationAdmission(
                        enabled = provide && !cleanupPending,
                        channelIndex = channelIndex,
                        channelIdentity = channelIdentity,
                        cleanupPending = cleanupPending,
                    )
            }
        everySuspend { uiPrefs.clearPreciseLocationCleanupPending(any()) } calls
            { call ->
                val admission =
                    preciseLocationAdmissions.getOrPut(call.arg(0)) { MutableStateFlow(PreciseLocationAdmission()) }
                admission.value = admission.value.copy(enabled = false, cleanupPending = false)
            }
        every { serviceRepository.connectionState } returns connectionStateFlow
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                connectionStateFlow.value = call.arg<ConnectionState>(0)
            }
        every { serviceNotifications.updateServiceStateNotification(any(), any()) } returns Unit
        every { commandSender.sendAdmin(any(), any(), any(), any()) } returns Unit
        everySuspend { commandSender.sendAdminAwaitForSession(any(), any(), any(), any(), any()) } returns true
        everySuspend { commandSender.sendPhonePositionForSession(any(), any(), any()) } returns true
        everySuspend { commandSender.requestTelemetryForSession(any(), any(), any(), any()) } returns true
        everySuspend { packetHandler.stopPacketQueueAndAwait() } returns Unit
        every { locationManager.stop() } returns Unit
        every { locationManager.restart() } returns Unit
        every { locationManager.setLocationAccessAllowed(any()) } returns Unit
        every { mqttManager.stop() } returns Unit
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap<Int, Node>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } returns Unit
        everySuspend { ntsocialChannelProvisioner.ensureDefaultChannel(any(), any()) } returns
            NtsocialChannelProvisionResult.AlreadyPresent
        everySuspend { ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any()) } returns
            NtsocialChannelProvisionResult.AlreadyPresent
        everySuspend { ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any()) } returns
            NtsocialChannelProvisionResult.AlreadyPresent
        everySuspend { ntsocialChannelProvisioner.currentDefaultChannelIndex() } returns 0
        everySuspend { ntsocialChannelProvisioner.currentDefaultChannelIndex(any()) } returns 0
        everySuspend { channelReliabilityManager.reconcileProtectedChannelSet() } returns
            ChannelReliabilityResult.NO_SNAPSHOT
        everySuspend { channelReliabilityManager.reconcileProtectedChannelSetForSession(any()) } returns
            ChannelReliabilityResult.NO_SNAPSHOT
        everySuspend { channelReliabilityManager.reconcileProtectedChannelSetForSession(any(), any()) } returns
            ChannelReliabilityResult.NO_SNAPSHOT
        everySuspend { ntsocialGatewayRepository.activateInboundSession(any()) } returns true
    }

    private fun createManager(
        scope: CoroutineScope,
        meshLocationManager: MeshLocationManager = locationManager,
        channelOperationLock: ChannelOperationLock = ChannelOperationLock(),
        channelMutationLock: ChannelMutationLock = ChannelMutationLock(),
        reliabilityManager: ChannelReliabilityManager = channelReliabilityManager,
    ): MeshConnectionManagerImpl = MeshConnectionManagerImpl(
        radioInterfaceService,
        serviceRepository,
        serviceBroadcasts,
        serviceNotifications,
        uiPrefs,
        packetHandler,
        nodeRepository,
        meshLocationManager,
        mqttManager,
        historyManager,
        radioConfigRepository,
        commandSender,
        sessionManager,
        nodeManager,
        analytics,
        packetRepository,
        workerManager,
        appWidgetUpdater,
        DataLayerHeartbeatSender(packetHandler),
        ntsocialChannelProvisioner,
        ntsocialGatewayRepository,
        reliabilityManager,
        channelOperationLock,
        channelMutationLock,
        scope,
    )

    @AfterTest fun tearDown() = Unit

    @Test
    fun `Connected state triggers broadcast and config start`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "State should be Connecting after radio Connected",
        )
        verify { serviceBroadcasts.broadcastConnection() }
    }

    @Test
    fun `Connected state sends pre-handshake heartbeat before config request`() = runTest(testDispatcher) {
        val sentPackets = mutableListOf<org.meshtastic.proto.ToRadio>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } calls
            { call ->
                sentPackets.add(call.arg(0))
            }

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Advance past PRE_HANDSHAKE_SETTLE_MS (100ms) but NOT the 30s stall guard timeout
        advanceTimeBy(200)

        // First ToRadio should be a heartbeat, second should be want_config_id
        assertEquals(2, sentPackets.size, "Expected heartbeat + want_config_id, got ${sentPackets.size} packets")
        val heartbeat = sentPackets[0]
        val wantConfig = sentPackets[1]

        assertEquals(true, heartbeat.heartbeat != null, "First packet should be a heartbeat")
        assertEquals(true, heartbeat.heartbeat!!.nonce != 0, "Heartbeat should have a non-zero nonce")
        assertEquals(
            com.ntsocial.meshlink.core.repository.HandshakeConstants.CONFIG_NONCE,
            wantConfig.want_config_id,
            "Second packet should be want_config_id with CONFIG_NONCE",
        )
    }

    @Test
    fun `exact config readback rejection never arms stale retry guard`() = runTest(testDispatcher) {
        every { packetHandler.sendToRadioForSession(any(), SESSION_EPOCH) } returns false
        manager = createManager(backgroundScope)
        connectionStateFlow.value = ConnectionState.Connecting

        assertFalse(manager.startConfigOnlyForSession(SESSION_EPOCH))
        advanceTimeBy(50_000L)

        verify(mode = VerifyMode.exactly(1)) { packetHandler.sendToRadioForSession(any(), SESSION_EPOCH) }
        verify(mode = VerifyMode.not) { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) }
    }

    @Test
    fun `retired Connected job cannot reopen packet admission after radio selection stops it`() =
        runTest(testDispatcher) {
            val operationLock = ChannelOperationLock()
            operationLock.withLock {
                manager = createManager(backgroundScope, channelOperationLock = operationLock)
                radioConnectionState.value = ConnectionState.Connected
                runCurrent()

                // Model selection while the stale pre-handshake job is waiting for the same operation lock.
                radioSessionState.value =
                    radioSessionState.value.copy(
                        epoch = SESSION_EPOCH + 1,
                        selectedDeviceAddress = "xBB:BB:BB:BB:BB:BB",
                        activeDeviceAddress = null,
                        transportConnectionState = ConnectionState.Disconnected,
                    )
                packetHandler.stopPacketQueueAndAwait()
            }
            runCurrent()

            verifySuspend(mode = VerifyMode.not) { packetHandler.resumePacketQueueAndAwait() }
            verify(mode = VerifyMode.not) { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) }
        }

    @Test
    fun `Disconnect during pre-handshake settle cancels config start`() = runTest(testDispatcher) {
        val sentPackets = mutableListOf<org.meshtastic.proto.ToRadio>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } calls
            { call ->
                sentPackets.add(call.arg(0))
            }
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Advance only 50ms — within the 100ms settle window
        advanceTimeBy(50)

        // Should have sent only the heartbeat so far, not want_config_id
        assertEquals(1, sentPackets.size, "Only heartbeat should be sent before settle completes")

        // Disconnect before the settle delay completes — should cancel the pending config start
        radioConnectionState.value = ConnectionState.Disconnected
        advanceTimeBy(200)

        // The want_config_id should NOT have been sent because the job was cancelled
        val configPackets = sentPackets.filter { it.want_config_id != null }
        assertEquals(0, configPackets.size, "want_config_id should not be sent after disconnect")
    }

    @Test
    fun `Disconnected state stops services`() = runTest(testDispatcher) {
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        manager = createManager(backgroundScope)
        // Transition to Connected first so that Disconnected actually does something
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "State should be Disconnected after radio Disconnected",
        )
        verifySuspend { packetHandler.stopPacketQueueAndAwait() }
        verify { locationManager.stop() }
        verify { mqttManager.stop() }
    }

    @Test
    fun `location feed follows preference connection fixed position reconnect and node switch`() =
        runTest(testDispatcher) {
            val recordingLocationManager = RecordingLocationManager()
            preciseLocationAdmissions.getOrPut(1) { MutableStateFlow(PreciseLocationAdmission()) }.value =
                preciseLocationAdmission()
            preciseLocationAdmissions.getOrPut(2) { MutableStateFlow(PreciseLocationAdmission()) }.value =
                preciseLocationAdmission()
            channelSetFlow.value = preciseLocationChannelSet()
            radioSessionState.value = radioSessionState.value.copy(configured = true)
            manager = createManager(backgroundScope, recordingLocationManager)
            advanceUntilIdle()
            recordingLocationManager.events.clear()

            nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))
            connectionStateFlow.value = ConnectionState.Connected
            advanceUntilIdle()

            assertEquals(true, manager.locationSharingRequested.value)
            assertEquals(true, manager.shouldProvideLocation.value)
            assertEquals(1, recordingLocationManager.events.count { it == "start" })

            connectionStateFlow.value = ConnectionState.Disconnected
            advanceUntilIdle()
            assertEquals(false, manager.shouldProvideLocation.value)

            connectionStateFlow.value = ConnectionState.Connected
            advanceUntilIdle()
            assertEquals(2, recordingLocationManager.events.count { it == "start" })

            localConfigFlow.value = LocalConfig(position = Config.PositionConfig(fixed_position = true))
            advanceUntilIdle()
            assertEquals(false, manager.locationSharingRequested.value)
            assertEquals(false, manager.shouldProvideLocation.value)

            localConfigFlow.value = LocalConfig(position = Config.PositionConfig(fixed_position = false))
            advanceUntilIdle()
            assertEquals(3, recordingLocationManager.events.count { it == "start" })

            nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 2))
            advanceUntilIdle()
            assertEquals(4, recordingLocationManager.events.count { it == "start" })
            assertEquals("stop", recordingLocationManager.events[recordingLocationManager.events.lastIndex - 1])

            manager.reconcileLocation()
            advanceUntilIdle()
            assertEquals(1, recordingLocationManager.events.count { it == "restart" })
        }

    @Test
    fun `manual location reconcile cannot bypass a disabled per-node preference`() = runTest(testDispatcher) {
        val recordingLocationManager = RecordingLocationManager()
        preciseLocationAdmissions.getOrPut(1) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            PreciseLocationAdmission(enabled = false, channelIndex = 1)
        manager = createManager(backgroundScope, recordingLocationManager)
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))
        connectionStateFlow.value = ConnectionState.Connected
        advanceUntilIdle()
        recordingLocationManager.events.clear()

        manager.reconcileLocation()
        advanceUntilIdle()

        assertEquals(false, manager.locationSharingRequested.value)
        assertEquals(false, manager.shouldProvideLocation.value)
        assertEquals(0, recordingLocationManager.events.count { it == "start" || it == "restart" })
    }

    @Test
    fun `location feed stops when the verified precise channel policy changes`() = runTest(testDispatcher) {
        val recordingLocationManager = RecordingLocationManager()
        preciseLocationAdmissions.getOrPut(1) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            preciseLocationAdmission()
        channelSetFlow.value = preciseLocationChannelSet()
        radioSessionState.value = radioSessionState.value.copy(configured = true)
        manager = createManager(backgroundScope, recordingLocationManager)
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))
        connectionStateFlow.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(true, manager.shouldProvideLocation.value)

        channelSetFlow.value =
            preciseLocationChannelSet()
                .copy(
                    settings =
                    preciseLocationChannelSet().settings.map { settings ->
                        settings.copy(module_settings = settings.module_settings?.copy(position_precision = 13))
                    },
                )
        advanceUntilIdle()

        assertEquals(true, manager.locationSharingRequested.value)
        assertEquals(false, manager.shouldProvideLocation.value)
        assertEquals("stop", recordingLocationManager.events.last())
    }

    @Test
    fun `channel mutation blocks an already queued location callback while preserving explicit request`() =
        runTest(testDispatcher) {
            val mutationLock = ChannelMutationLock()
            val recordingLocationManager = RecordingLocationManager()
            preciseLocationAdmissions.getOrPut(1) { MutableStateFlow(PreciseLocationAdmission()) }.value =
                preciseLocationAdmission()
            channelSetFlow.value = preciseLocationChannelSet()
            radioSessionState.value = radioSessionState.value.copy(configured = true)
            manager = createManager(backgroundScope, recordingLocationManager, channelMutationLock = mutationLock)
            nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))
            connectionStateFlow.value = ConnectionState.Connected
            advanceUntilIdle()
            assertEquals(true, manager.shouldProvideLocation.value)

            val releaseMutation = CompletableDeferred<Unit>()
            backgroundScope.launch { mutationLock.withLock { releaseMutation.await() } }
            runCurrent()
            assertEquals(1, mutationLock.activeOrPendingOwners.value)

            recordingLocationManager.emit(org.meshtastic.proto.Position(latitude_i = 250_000_000))
            runCurrent()
            verifySuspend(mode = VerifyMode.not) { commandSender.sendPhonePositionForSession(any(), any(), any()) }

            advanceUntilIdle()
            assertEquals(true, manager.locationSharingRequested.value)
            assertEquals(false, manager.shouldProvideLocation.value)

            releaseMutation.complete(Unit)
            advanceUntilIdle()
            assertEquals(true, manager.locationSharingRequested.value)
            assertEquals(true, manager.shouldProvideLocation.value)
        }

    @Test
    fun `admitted phone position completes before a later channel mutation can enter`() = runTest(testDispatcher) {
        val mutationLock = ChannelMutationLock()
        val recordingLocationManager = RecordingLocationManager()
        val positionSendEntered = CompletableDeferred<Unit>()
        val releasePositionSend = CompletableDeferred<Unit>()
        val mutationEntered = CompletableDeferred<Unit>()
        preciseLocationAdmissions.getOrPut(1) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            preciseLocationAdmission()
        channelSetFlow.value = preciseLocationChannelSet()
        radioSessionState.value = radioSessionState.value.copy(configured = true)
        everySuspend { commandSender.sendPhonePositionForSession(any(), any(), any()) } calls
            {
                positionSendEntered.complete(Unit)
                releasePositionSend.await()
                true
            }
        manager = createManager(backgroundScope, recordingLocationManager, channelMutationLock = mutationLock)
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))
        connectionStateFlow.value = ConnectionState.Connected
        advanceUntilIdle()

        recordingLocationManager.emit(org.meshtastic.proto.Position(latitude_i = 250_000_001))
        runCurrent()
        positionSendEntered.await()

        backgroundScope.launch { mutationLock.withLock { mutationEntered.complete(Unit) } }
        runCurrent()
        assertEquals(1, mutationLock.activeOrPendingOwners.value)
        assertFalse(mutationEntered.isCompleted)

        releasePositionSend.complete(Unit)
        advanceUntilIdle()
        mutationEntered.await()
        verifySuspend {
            commandSender.sendPhonePositionForSession(
                org.meshtastic.proto.Position(latitude_i = 250_000_001),
                1,
                SESSION_EPOCH,
            )
        }
    }

    @Test
    fun `retired location callback cannot feed a replacement radio session`() = runTest(testDispatcher) {
        val recordingLocationManager = RecordingLocationManager()
        preciseLocationAdmissions.getOrPut(1) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            preciseLocationAdmission()
        channelSetFlow.value = preciseLocationChannelSet()
        radioSessionState.value = radioSessionState.value.copy(configured = true)
        manager = createManager(backgroundScope, recordingLocationManager)
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))
        connectionStateFlow.value = ConnectionState.Connected
        advanceUntilIdle()
        assertEquals(1, recordingLocationManager.callbackCount)

        connectionStateFlow.value = ConnectionState.Disconnected
        radioSessionState.value = radioSessionState.value.copy(epoch = SESSION_EPOCH + 1, configured = true)
        advanceUntilIdle()

        recordingLocationManager.emitFrom(
            callbackIndex = 0,
            position = org.meshtastic.proto.Position(latitude_i = 250_000_002),
        )
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.not) { commandSender.sendPhonePositionForSession(any(), any(), any()) }
    }

    @Test
    fun `DeviceSleep behavior when power saving is off maps to Disconnected`() = runTest(testDispatcher) {
        // Power saving disabled + Role CLIENT
        val config =
            LocalConfig(
                power = Config.PowerConfig(is_power_saving = false),
                device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT),
            )
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "State should be Disconnected when power saving is off",
        )
    }

    @Test
    fun `DeviceSleep behavior when power saving is on stays in DeviceSleep`() = runTest(testDispatcher) {
        // Power saving enabled
        val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true))
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            ConnectionState.DeviceSleep,
            serviceRepository.connectionState.value,
            "State should stay in DeviceSleep when power saving is on",
        )
    }

    @Test
    fun `onRadioConfigLoaded enqueues queued packets and sets time`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        val packetId = 456
        everySuspend { packetRepository.getQueuedPackets() } returns listOf(dataPacket)
        every { workerManager.enqueueSendMessage(any()) } returns Unit

        manager.onRadioConfigLoaded()
        advanceUntilIdle()

        verify { workerManager.enqueueSendMessage(packetId) }
    }

    @Test
    fun `onNodeDbReady starts MQTT and requests history`() = runTest(testDispatcher) {
        val moduleConfig =
            LocalModuleConfig(
                mqtt = ModuleConfig.MQTTConfig(enabled = true, proxy_to_client_enabled = true),
                store_forward = ModuleConfig.StoreForwardConfig(enabled = true),
            )
        moduleConfigFlow.value = moduleConfig
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        every { historyManager.requestHistoryReplay(any(), any(), any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        manager.onNodeDbReady(SESSION_EPOCH)
        advanceUntilIdle()

        verify { radioInterfaceService.markCurrentSessionConfigured(SESSION_EPOCH) }
        verifySuspend(mode = VerifyMode.exactly(1)) {
            ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH)
        }
        verifySuspend(mode = VerifyMode.exactly(2)) {
            commandSender.sendAdminAwaitForSession(SESSION_EPOCH, 123, any(), any(), any())
        }
        verify(mode = VerifyMode.not) { commandSender.sendAdmin(any(), any(), any(), any()) }
        verifySuspend(mode = VerifyMode.exactly(2)) {
            commandSender.requestTelemetryForSession(SESSION_EPOCH, any(), 123, any())
        }
        verify(mode = VerifyMode.not) { commandSender.requestTelemetry(any(), any(), any()) }
        verify { mqttManager.startProxy(true, true) }
        verify { historyManager.requestHistoryReplay(any(), any(), any(), any()) }
        verifySuspend { ntsocialChannelProvisioner.ensureDefaultChannelForSession(123, 8, SESSION_EPOCH, any()) }
    }

    @Test
    fun `Stage 2 retries durable cleanup against cached slot 4 p32 before gateway activation`() =
        runTest(testDispatcher) {
            val cachedChannelSet = cleanupPendingChannelSet()
            val cleanupReliability =
                RecordingCleanupReliabilityManager(
                    currentChannelSet = { channelSetFlow.value },
                    publishReadback = { channelSetFlow.value = it },
                    result = ChannelReliabilityResult.VERIFIED,
                )
            every { nodeManager.myNodeNum } returns MutableStateFlow(123)
            every { nodeManager.getMyNodeInfo() } returns null
            preciseLocationAdmissions.getOrPut(123) { MutableStateFlow(PreciseLocationAdmission()) }.value =
                PreciseLocationAdmission(
                    enabled = false,
                    channelIndex = 4,
                    channelIdentity = "retired-slot-4",
                    cleanupPending = true,
                )
            channelSetFlow.value = cachedChannelSet
            manager = createManager(backgroundScope, reliabilityManager = cleanupReliability)

            assertTrue(manager.onNodeDbReady(SESSION_EPOCH))
            advanceUntilIdle()

            assertEquals(1, cleanupReliability.applyCount)
            assertEquals(123, cleanupReliability.expectedNodeNum)
            assertTrue(cleanupReliability.mutationLeaseProvided)
            assertTrue(cleanupReliability.requireStableSlots)
            assertEquals(ChannelWriteOrder.PRECISE_POSITION_TARGET_LAST, cleanupReliability.writeOrder)
            assertNull(cleanupReliability.desiredChannelSet.lora_config)
            assertEquals(
                listOf(0, 0, 0, 0, 0),
                cleanupReliability.desiredChannelSet.settings.map { it.module_settings?.position_precision ?: 0 },
            )
            assertFalse(preciseLocationAdmissions.getValue(123).value.enabled)
            assertFalse(preciseLocationAdmissions.getValue(123).value.cleanupPending)
            assertEquals(1, cleanupReliability.reconcileCount)
            verifySuspend(mode = VerifyMode.exactly(1)) {
                ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH)
            }
        }

    @Test
    fun `Stage 2 clears durable cleanup without rewriting an already all-p0 radio`() = runTest(testDispatcher) {
        val cachedChannelSet = cleanupPendingChannelSet()
        val allP0ChannelSet =
            PreciseLocationChannelSetPlanner.disable(cachedChannelSet)
                .copy(lora_config = cachedChannelSet.lora_config)
        val cleanupReliability =
            RecordingCleanupReliabilityManager(
                currentChannelSet = { channelSetFlow.value },
                publishReadback = { channelSetFlow.value = it },
                result = ChannelReliabilityResult.SESSION_UNAVAILABLE,
            )
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        preciseLocationAdmissions.getOrPut(123) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            PreciseLocationAdmission(
                enabled = false,
                channelIndex = 4,
                channelIdentity = "retired-slot-4",
                cleanupPending = true,
            )
        channelSetFlow.value = allP0ChannelSet
        manager = createManager(backgroundScope, reliabilityManager = cleanupReliability)

        assertTrue(manager.onNodeDbReady(SESSION_EPOCH))
        advanceUntilIdle()

        assertEquals(0, cleanupReliability.applyCount)
        assertFalse(preciseLocationAdmissions.getValue(123).value.cleanupPending)
        assertEquals(1, cleanupReliability.reconcileCount)
        verifySuspend(mode = VerifyMode.exactly(1)) {
            ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH)
        }
    }

    @Test
    fun `failed durable cleanup remains pending and keeps Stage 2 ingress closed`() = runTest(testDispatcher) {
        val cleanupReliability =
            RecordingCleanupReliabilityManager(
                currentChannelSet = ::cleanupPendingChannelSet,
                publishReadback = {},
                result = ChannelReliabilityResult.SESSION_UNAVAILABLE,
            )
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        preciseLocationAdmissions.getOrPut(123) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            PreciseLocationAdmission(
                enabled = false,
                channelIndex = 4,
                channelIdentity = "retired-slot-4",
                cleanupPending = true,
            )
        channelSetFlow.value = cleanupPendingChannelSet()
        manager = createManager(backgroundScope, reliabilityManager = cleanupReliability)

        assertTrue(manager.onNodeDbReady(SESSION_EPOCH))
        advanceUntilIdle()

        assertEquals(1, cleanupReliability.applyCount)
        assertTrue(preciseLocationAdmissions.getValue(123).value.cleanupPending)
        assertFalse(preciseLocationAdmissions.getValue(123).value.enabled)
        assertEquals(0, cleanupReliability.reconcileCount)
        verifySuspend { ntsocialGatewayRepository.invalidateInboundSession() }
        verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(any()) }
        verifySuspend(mode = VerifyMode.not) {
            ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any())
        }
    }

    @Test
    fun `ambiguous channel repair failure keeps gateway ingress closed`() = runTest(testDispatcher) {
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        everySuspend {
            channelReliabilityManager.reconcileProtectedChannelSetForSession(SESSION_EPOCH, any())
        } returns ChannelReliabilityResult.READBACK_FAILED
        manager = createManager(backgroundScope)

        manager.onNodeDbReady(SESSION_EPOCH)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH) }
        verifySuspend(mode = VerifyMode.not) {
            ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any())
        }
    }

    @Test
    fun `pending channel repair keeps gateway ingress closed until a later verified session`() =
        runTest(testDispatcher) {
            every { nodeManager.myNodeNum } returns MutableStateFlow(123)
            every { nodeManager.getMyNodeInfo() } returns null
            everySuspend {
                channelReliabilityManager.reconcileProtectedChannelSetForSession(SESSION_EPOCH, any())
            } returns ChannelReliabilityResult.VERIFICATION_PENDING
            manager = createManager(backgroundScope)

            manager.onNodeDbReady(SESSION_EPOCH)
            advanceUntilIdle()

            verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH) }
            verifySuspend(mode = VerifyMode.not) {
                ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any())
            }
        }

    @Test
    fun `unconfirmed channel repair keeps provisioner and gateway ingress closed`() = runTest(testDispatcher) {
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        everySuspend {
            channelReliabilityManager.reconcileProtectedChannelSetForSession(SESSION_EPOCH, any())
        } returns ChannelReliabilityResult.SESSION_UNAVAILABLE
        manager = createManager(backgroundScope)

        manager.onNodeDbReady(SESSION_EPOCH)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH) }
        verifySuspend(mode = VerifyMode.not) {
            ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any())
        }
    }

    @Test
    fun `ambiguous provisioning acknowledgement timeout keeps gateway ingress closed`() = runTest(testDispatcher) {
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        everySuspend {
            ntsocialChannelProvisioner.ensureDefaultChannelForSession(123, 8, SESSION_EPOCH, any())
        } returns NtsocialChannelProvisionResult.RadioRejected
        manager = createManager(backgroundScope)

        manager.onNodeDbReady(SESSION_EPOCH)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH) }
        verifySuspend { ntsocialChannelProvisioner.ensureDefaultChannelForSession(123, 8, SESSION_EPOCH, any()) }
    }

    @Test
    fun `same-address reconnect during provisioning keeps gateway ingress closed`() = runTest(testDispatcher) {
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        everySuspend {
            ntsocialChannelProvisioner.ensureDefaultChannelForSession(123, 8, SESSION_EPOCH, any())
        } calls
            {
                radioSessionState.value = radioSessionState.value.copy(epoch = SESSION_EPOCH + 1)
                null
            }
        manager = createManager(backgroundScope)

        manager.onNodeDbReady(SESSION_EPOCH)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH) }
        verifySuspend { ntsocialChannelProvisioner.ensureDefaultChannelForSession(123, 8, SESSION_EPOCH, any()) }
    }

    @Test
    fun `stale node database completion sends no admin or provisioning work`() = runTest(testDispatcher) {
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        radioSessionState.value = radioSessionState.value.copy(epoch = SESSION_EPOCH + 1)
        manager = createManager(backgroundScope)

        val accepted = manager.onNodeDbReady(SESSION_EPOCH)
        advanceUntilIdle()

        assertFalse(accepted)
        verify { radioInterfaceService.markCurrentSessionConfigured(SESSION_EPOCH) }
        verify(mode = VerifyMode.not) { commandSender.sendAdmin(any(), any(), any(), any()) }
        verifySuspend(mode = VerifyMode.not) {
            commandSender.sendAdminAwaitForSession(any(), any(), any(), any(), any())
        }
        verify(mode = VerifyMode.not) { commandSender.requestTelemetry(any(), any(), any()) }
        verifySuspend(mode = VerifyMode.not) {
            commandSender.requestTelemetryForSession(any(), any(), any(), any())
        }
        verifySuspend(mode = VerifyMode.not) {
            ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any())
        }
        verifySuspend(mode = VerifyMode.not) {
            channelReliabilityManager.reconcileProtectedChannelSetForSession(any(), any())
        }
    }

    @Test
    fun `gateway does not activate while protected repair is still running`() = runTest(testDispatcher) {
        val repairStarted = CompletableDeferred<Unit>()
        val releaseRepair = CompletableDeferred<Unit>()
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { nodeManager.getMyNodeInfo() } returns null
        everySuspend {
            channelReliabilityManager.reconcileProtectedChannelSetForSession(SESSION_EPOCH, any())
        } calls
            {
                repairStarted.complete(Unit)
                releaseRepair.await()
                ChannelReliabilityResult.NO_SNAPSHOT
            }
        manager = createManager(backgroundScope)

        assertEquals(true, manager.onNodeDbReady(SESSION_EPOCH))
        repairStarted.await()
        verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH) }

        releaseRepair.complete(Unit)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.exactly(1)) {
            ntsocialGatewayRepository.activateInboundSession(SESSION_EPOCH)
        }
    }

    @Test
    fun `same-address reconnect at startup admin admission stops all later completion work`() =
        runTest(testDispatcher) {
            every { nodeManager.myNodeNum } returns MutableStateFlow(123)
            every { nodeManager.getMyNodeInfo() } returns null
            var admissions = 0
            everySuspend { commandSender.sendAdminAwaitForSession(SESSION_EPOCH, 123, any(), any(), any()) } calls
                {
                    admissions += 1
                    radioSessionState.value = radioSessionState.value.copy(epoch = SESSION_EPOCH + 1)
                    false
                }
            manager = createManager(backgroundScope)

            assertFalse(manager.onNodeDbReady(SESSION_EPOCH))
            advanceUntilIdle()

            assertEquals(1, admissions)
            verify(mode = VerifyMode.not) { commandSender.sendAdmin(any(), any(), any(), any()) }
            verifySuspend(mode = VerifyMode.not) {
                channelReliabilityManager.reconcileProtectedChannelSetForSession(any(), any())
            }
            verifySuspend(mode = VerifyMode.not) {
                ntsocialChannelProvisioner.ensureDefaultChannelForSession(any(), any(), any(), any())
            }
            verifySuspend(mode = VerifyMode.not) { ntsocialGatewayRepository.activateInboundSession(any()) }
        }

    @Test
    fun `DeviceSleep timeout is capped at MAX_SLEEP_TIMEOUT_SECONDS for high ls_secs`() = runTest(testDispatcher) {
        // Router with ls_secs=3600 — previously this created a 3630s timeout.
        // With the cap, it should be clamped to 300s.
        val config =
            LocalConfig(
                power = Config.PowerConfig(is_power_saving = true, ls_secs = 3600),
                device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER),
            )
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        // Transition to Connected then DeviceSleep
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()
        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            ConnectionState.DeviceSleep,
            serviceRepository.connectionState.value,
            "Should be in DeviceSleep initially",
        )

        // Advance 300 seconds (the cap) + 1 second to trigger the timeout.
        advanceTimeBy(301_000L)

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Should transition to Disconnected after capped timeout (300s), not the raw 3630s",
        )
    }

    @Test
    fun `rapid state transitions are serialized by connectionMutex`() = runTest(testDispatcher) {
        // Power saving enabled so DeviceSleep is preserved (not mapped to Disconnected)
        val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true))
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        // Record every state transition so we can verify ordering
        val observed = mutableListOf<ConnectionState>()
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                val state = call.arg<ConnectionState>(0)
                observed.add(state)
                connectionStateFlow.value = state
            }

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        // Rapid-fire: Connected -> DeviceSleep -> Disconnected without yielding between them.
        // Without the Mutex, the intermediate DeviceSleep could be missed or applied out of order.
        radioConnectionState.value = ConnectionState.Connected
        radioConnectionState.value = ConnectionState.DeviceSleep
        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        // Verify final state
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Final state should be Disconnected after rapid transitions",
        )

        // Verify that all intermediate states were observed in correct order.
        // Connected triggers handleConnected() which sets Connecting (handshake start),
        // then DeviceSleep, then Disconnected.
        assertEquals(
            listOf(ConnectionState.Connecting, ConnectionState.DeviceSleep, ConnectionState.Disconnected),
            observed,
            "State transitions should be serialized in order: Connecting -> DeviceSleep -> Disconnected",
        )
    }

    @Test
    fun `concurrent sleep-timeout and radio state change are serialized`() {
        val standardDispatcher = StandardTestDispatcher()
        runTest(standardDispatcher) {
            // Power saving enabled with a short ls_secs so the sleep timeout fires quickly
            val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true, ls_secs = 1))
            every { radioConfigRepository.localConfigFlow } returns flowOf(config)
            every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

            val observed = mutableListOf<ConnectionState>()
            every { serviceRepository.setConnectionState(any()) } calls
                { call ->
                    val state = call.arg<ConnectionState>(0)
                    observed.add(state)
                    connectionStateFlow.value = state
                }

            manager = createManager(backgroundScope)
            advanceUntilIdle()

            // Transition to Connected -> DeviceSleep to start the sleep timer
            radioConnectionState.value = ConnectionState.Connected
            advanceUntilIdle()
            radioConnectionState.value = ConnectionState.DeviceSleep
            advanceUntilIdle()

            observed.clear()

            // Before the sleep timeout fires, emit Connected from the radio (simulating device
            // waking up). Then let the timeout fire. The mutex ensures they don't race.
            radioConnectionState.value = ConnectionState.Connected
            // Advance past the sleep timeout (ls_secs=1 + 30s base = 31s)
            advanceTimeBy(32_000L)
            advanceUntilIdle()

            // The Connected transition should have cancelled the sleep timeout, so we should
            // end up in Connecting (from handleConnected), NOT Disconnected (from timeout).
            assertEquals(
                ConnectionState.Connecting,
                serviceRepository.connectionState.value,
                "Connected should cancel the sleep timeout; final state should be Connecting",
            )
        }
    }

    private class RecordingCleanupReliabilityManager(
        private val currentChannelSet: () -> ChannelSet,
        private val publishReadback: (ChannelSet) -> Unit,
        private val result: ChannelReliabilityResult,
    ) : ChannelReliabilityManager {
        override val isProtected = MutableStateFlow(false)

        var applyCount = 0
            private set

        var reconcileCount = 0
            private set

        var expectedNodeNum: Int? = null
            private set

        var mutationLeaseProvided = false
            private set

        var requireStableSlots = false
            private set

        var writeOrder = ChannelWriteOrder.SLOT_INDEX
            private set

        lateinit var desiredChannelSet: ChannelSet
            private set

        override suspend fun applyAndVerify(channelSet: ChannelSet): ChannelReliabilityResult = result

        override suspend fun applyCurrentAndVerify(
            expectedNodeNum: Int,
            mutationLease: ChannelMutationLock.Lease?,
            requireStableSlots: Boolean,
            writeOrder: ChannelWriteOrder,
            transform: (ChannelSet) -> ChannelSet,
        ): ChannelReliabilityResult {
            applyCount += 1
            this.expectedNodeNum = expectedNodeNum
            mutationLeaseProvided = mutationLease != null
            this.requireStableSlots = requireStableSlots
            this.writeOrder = writeOrder
            val current = currentChannelSet()
            desiredChannelSet = transform(current)
            if (result == ChannelReliabilityResult.VERIFIED) {
                publishReadback(desiredChannelSet.copy(lora_config = current.lora_config))
            }
            return result
        }

        override suspend fun protectCurrentChannelSet(): ChannelReliabilityResult = ChannelReliabilityResult.PROTECTED

        override suspend fun disableProtection(): ChannelReliabilityResult =
            ChannelReliabilityResult.PROTECTION_DISABLED

        override suspend fun reconcileProtectedChannelSet(): ChannelReliabilityResult {
            reconcileCount += 1
            return ChannelReliabilityResult.NO_SNAPSHOT
        }
    }

    private class RecordingLocationManager : MeshLocationManager {
        val events = mutableListOf<String>()
        private val callbacks = mutableListOf<(org.meshtastic.proto.Position) -> Unit>()
        val callbackCount: Int
            get() = callbacks.size

        override fun start(scope: CoroutineScope, sendPositionFn: (org.meshtastic.proto.Position) -> Unit) {
            events += "start"
            callbacks += sendPositionFn
        }

        fun emit(position: org.meshtastic.proto.Position) = callbacks.last().invoke(position)

        fun emitFrom(callbackIndex: Int, position: org.meshtastic.proto.Position) =
            callbacks[callbackIndex].invoke(position)

        override fun restart() {
            events += "restart"
        }

        override fun setLocationAccessAllowed(allowed: Boolean) = Unit

        override fun stop() {
            events += "stop"
        }
    }

    private fun preciseLocationChannelSet(): ChannelSet = ChannelSet(
        settings =
        listOf(
            ChannelSettings(name = "Primary", module_settings = ModuleSettings(position_precision = 0)),
            ChannelSettings(name = "Private", module_settings = ModuleSettings(position_precision = 32)),
        ),
    )

    private fun cleanupPendingChannelSet(): ChannelSet = ChannelSet(
        settings =
        listOf(
            ChannelSettings(name = "Primary", module_settings = ModuleSettings(position_precision = 15)),
            ChannelSettings(name = "Private 1", module_settings = ModuleSettings(position_precision = 13)),
            ChannelSettings(name = "Private 2", module_settings = ModuleSettings(position_precision = 13)),
            ChannelSettings(name = "Private 3", module_settings = ModuleSettings(position_precision = 13)),
            ChannelSettings(name = "NTsocial", module_settings = ModuleSettings(position_precision = 32)),
        ),
        lora_config = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW),
    )

    private fun preciseLocationAdmission(): PreciseLocationAdmission = PreciseLocationAdmission(
        enabled = true,
        channelIndex = 1,
        channelIdentity =
        requireNotNull(PreciseLocationChannelSetPlanner.channelIdentity(preciseLocationChannelSet(), 1)),
    )

    private companion object {
        const val SESSION_EPOCH = 42L
        const val RADIO_ADDRESS = "xAA:BB:CC:DD:EE:FF"
    }
}
