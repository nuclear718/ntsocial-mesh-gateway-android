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
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
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
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
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
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private val dataPacket = DataPacket(id = 456, time = 0L, to = "0", from = "0", bytes = null, dataType = 0)

    private val radioConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig())
    private val nodeLocationPrefs = mutableMapOf<Int, MutableStateFlow<Boolean>>()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var manager: MeshConnectionManagerImpl

    @BeforeTest
    fun setUp() {
        every { radioInterfaceService.connectionState } returns radioConnectionState
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
        every { uiPrefs.shouldProvideNodeLocation(any()) } calls
            { call ->
                nodeLocationPrefs.getOrPut(call.arg(0)) { MutableStateFlow(false) }
            }
        every { serviceRepository.connectionState } returns connectionStateFlow
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                connectionStateFlow.value = call.arg<ConnectionState>(0)
            }
        every { serviceNotifications.updateServiceStateNotification(any(), any()) } returns Unit
        every { commandSender.sendAdmin(any(), any(), any(), any()) } returns Unit
        every { packetHandler.stopPacketQueue() } returns Unit
        every { locationManager.stop() } returns Unit
        every { locationManager.restart() } returns Unit
        every { locationManager.setLocationAccessAllowed(any()) } returns Unit
        every { mqttManager.stop() } returns Unit
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap<Int, Node>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } returns Unit
        everySuspend { ntsocialChannelProvisioner.ensureDefaultChannel(any(), any()) } returns
            NtsocialChannelProvisionResult.AlreadyPresent
        everySuspend { ntsocialChannelProvisioner.currentDefaultChannelIndex() } returns 0
    }

    private fun createManager(
        scope: CoroutineScope,
        meshLocationManager: MeshLocationManager = locationManager,
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
        mock<ChannelReliabilityManager>(MockMode.autofill).also { reliabilityManager ->
            everySuspend { reliabilityManager.reconcileProtectedChannelSet() } returns
                ChannelReliabilityResult.NO_SNAPSHOT
        },
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
        verify { packetHandler.stopPacketQueue() }
        verify { locationManager.stop() }
        verify { mqttManager.stop() }
    }

    @Test
    fun `location feed follows preference connection fixed position reconnect and node switch`() =
        runTest(testDispatcher) {
            val recordingLocationManager = RecordingLocationManager()
            nodeLocationPrefs.getOrPut(1) { MutableStateFlow(false) }.value = true
            nodeLocationPrefs.getOrPut(2) { MutableStateFlow(false) }.value = true
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
        nodeLocationPrefs.getOrPut(1) { MutableStateFlow(false) }.value = false
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
        every { commandSender.requestTelemetry(any(), any(), any()) } returns Unit
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        every { historyManager.requestHistoryReplay(any(), any(), any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        manager.onNodeDbReady()
        advanceUntilIdle()

        verify { mqttManager.startProxy(true, true) }
        verify { historyManager.requestHistoryReplay(any(), any(), any(), any()) }
        verifySuspend { ntsocialChannelProvisioner.ensureDefaultChannel(123, 8) }
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

    private class RecordingLocationManager : MeshLocationManager {
        val events = mutableListOf<String>()

        override fun start(scope: CoroutineScope, sendPositionFn: (org.meshtastic.proto.Position) -> Unit) {
            events += "start"
        }

        override fun restart() {
            events += "restart"
        }

        override fun setLocationAccessAllowed(allowed: Boolean) = Unit

        override fun stop() {
            events += "stop"
        }
    }
}
