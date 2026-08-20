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

import com.ntsocial.meshlink.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import com.ntsocial.meshlink.core.domain.usecase.session.EnsureSessionResult
import com.ntsocial.meshlink.core.domain.usecase.settings.ChannelReliabilityManagerImpl
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.HandshakeConstants
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.NotificationPrefs
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeRadioConfigRepository
import com.ntsocial.meshlink.core.testing.FakeRadioInterfaceService
import com.ntsocial.meshlink.core.testing.TestDataFactory
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

class ChannelReliabilityReadbackIntegrationTest {
    @Test
    fun `verified apply releases operation lock for production readback commit and stops before Stage 2`() = runTest {
        val operationLock = ChannelOperationLock()
        val mutationLock = ChannelMutationLock()
        val radioConfigRepository = FakeRadioConfigRepository()
        val nodeRepository = FakeNodeRepository()
        val radioInterfaceService = FakeRadioInterfaceService(backgroundScope)
        val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
        val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        val serviceBroadcasts = mock<ServiceBroadcasts>(MockMode.autofill)
        val analytics = mock<PlatformAnalytics>(MockMode.autofill)
        val notificationPrefs = mock<NotificationPrefs>(MockMode.autofill)
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val gatewayRepository = mock<NtsocialGatewayRepository>(MockMode.autofill)
        val ensureSession = mock<EnsureRemoteAdminSessionUseCase>(MockMode.autofill)
        val meshPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 32)
        val commandSender = RoutingAckCommandSender(meshPackets) { radioInterfaceService.radioSessionState.value.epoch }
        val desiredLora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW)
        val desired =
            ChannelSet(
                settings =
                listOf(
                    ChannelSettings(name = "primary", psk = byteArrayOf(1).toByteString()),
                    ChannelSettings(name = "secondary", psk = byteArrayOf(2).toByteString()),
                ),
                lora_config = desiredLora,
            )

        radioInterfaceService.setDeviceAddress(RADIO_ADDRESS)
        radioInterfaceService.onConnect()
        val sessionEpoch = radioInterfaceService.radioSessionState.value.epoch
        check(radioInterfaceService.markCurrentSessionConfigured(sessionEpoch))
        radioConfigRepository.setLocalConfigDirect(LocalConfig(lora = desiredLora))
        radioConfigRepository.setCompleteChannelReadback(
            ChannelSet(settings = listOf(ChannelSettings(name = "old-primary")), lora_config = desiredLora),
        )
        nodeRepository.setMyNodeInfo(
            TestDataFactory.createMyNodeInfo(myNodeNum = NODE_NUM).copy(deviceId = "0011223344556677", maxChannels = 8),
        )
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        every { serviceRepository.meshPacketFlow } returns meshPackets
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { nodeManager.myNodeNum } returns MutableStateFlow(NODE_NUM)
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(false)
        every { notificationPrefs.nodeEventsEnabled } returns MutableStateFlow(true)
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } returns Unit
        everySuspend { ensureSession(any()) } returns EnsureSessionResult.AlreadyActive
        everySuspend { ensureSession(any(), any()) } returns EnsureSessionResult.AlreadyActive
        everySuspend { gatewayRepository.activateInboundSession(sessionEpoch) } returns true

        val collector = HandshakeChannelSetCollector(radioConfigRepository)
        lateinit var flowManager: MeshConfigFlowManagerImpl
        flowManager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                connectionManager = lazy { connectionManager },
                radioInterfaceService = radioInterfaceService,
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
                serviceBroadcasts = serviceBroadcasts,
                analytics = analytics,
                commandSender = commandSender,
                heartbeatSender = DataLayerHeartbeatSender(packetHandler),
                notificationPrefs = notificationPrefs,
                channelSetCollector = collector,
                channelOperationLock = operationLock,
                scope = backgroundScope,
            )
        every { connectionManager.startConfigOnlyForSession(sessionEpoch) } calls
            {
                backgroundScope.launch {
                    flowManager.handleMyInfo(
                        ProtoMyNodeInfo(my_node_num = NODE_NUM, device_id = byteArrayOf(1, 2, 3).toByteString()),
                    )
                    desired.settings.forEachIndexed { index, settings ->
                        collector.captureChannel(
                            Channel(
                                index = index,
                                role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
                                settings = settings,
                            ),
                        )
                    }
                    collector.captureConfig(Config(lora = desiredLora))
                    flowManager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
                }
                true
            }

        // Exact readback ownership is available only after the prior FULL handshake has reached Stage 2 Complete.
        flowManager.handleMyInfo(
            ProtoMyNodeInfo(my_node_num = NODE_NUM, device_id = byteArrayOf(1, 2, 3).toByteString()),
        )
        collector.captureChannel(
            Channel(index = 0, role = Channel.Role.PRIMARY, settings = ChannelSettings(name = "old-primary")),
        )
        collector.captureConfig(Config(lora = desiredLora))
        flowManager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        testScheduler.runCurrent()
        flowManager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        testScheduler.runCurrent()

        val reliabilityManager =
            ChannelReliabilityManagerImpl(
                commandSender = commandSender,
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                channelSnapshotRepository = InMemoryChannelSnapshotRepository(),
                ensureRemoteAdminSession = ensureSession,
                meshConfigFlowManager = lazy { flowManager },
                operationLock = operationLock,
                mutationLock = mutationLock,
                radioInterfaceService = radioInterfaceService,
                ntsocialGatewayRepository = gatewayRepository,
                serviceScope = backgroundScope,
            )

        assertEquals(ChannelReliabilityResult.VERIFIED, reliabilityManager.applyAndVerify(desired))
        assertEquals(desired, radioConfigRepository.currentChannelSet)
        verifySuspend(mode = VerifyMode.exactly(1)) { gatewayRepository.activateInboundSession(sessionEpoch) }
        // The one call belongs to the priming FULL handshake; READBACK_ONLY must not start a second Stage 2.
        verifySuspend(mode = VerifyMode.exactly(1)) { connectionManager.onNodeDbReady(any()) }
    }

    private class InMemoryChannelSnapshotRepository : ChannelSnapshotRepository {
        private val snapshots = mutableMapOf<String, ChannelProtectionSnapshot>()

        override suspend fun get(stableDeviceIdentity: String): ChannelProtectionSnapshot? =
            snapshots[stableDeviceIdentity]

        override suspend fun save(stableDeviceIdentity: String, snapshot: ChannelProtectionSnapshot) {
            snapshots[stableDeviceIdentity] = snapshot
        }

        override suspend fun clear(stableDeviceIdentity: String) {
            snapshots.remove(stableDeviceIdentity)
        }
    }

    private class RoutingAckCommandSender(
        private val meshPackets: MutableSharedFlow<MeshPacket>,
        private val currentEpoch: () -> Long,
    ) : CommandSender {
        private var nextPacketId = 1

        override fun getCurrentPacketId(): Long = nextPacketId.toLong()

        override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

        override fun getCachedChannelSet(): ChannelSet = ChannelSet()

        override fun generatePacketId(): Int = nextPacketId++

        override fun sendData(p: DataPacket) = Unit

        override fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) = Unit

        override suspend fun sendAdminAwait(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean = sendAndAck(destNum, requestId)

        override suspend fun sendAdminAwaitForSession(
            expectedRadioSessionEpoch: Long,
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean = expectedRadioSessionEpoch == currentEpoch() && sendAndAck(destNum, requestId)

        private suspend fun sendAndAck(destNum: Int, requestId: Int): Boolean {
            meshPackets.emit(
                MeshPacket(
                    from = destNum,
                    decoded =
                    Data(
                        portnum = PortNum.ROUTING_APP,
                        request_id = requestId,
                        payload = Routing(error_reason = Routing.Error.NONE).encode().toByteString(),
                    ),
                ),
            )
            return true
        }

        override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) = Unit

        override fun requestPosition(destNum: Int, currentPosition: Position) = Unit

        override fun requestPositionOnChannel(destNum: Int, currentPosition: Position, channelIndex: Int) = Unit

        override fun setFixedPosition(destNum: Int, pos: Position) = Unit

        override fun requestUserInfo(destNum: Int) = Unit

        override fun requestTraceroute(requestId: Int, destNum: Int) = Unit

        override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) = Unit

        override fun requestNeighborInfo(requestId: Int, destNum: Int) = Unit
    }

    private companion object {
        const val NODE_NUM = 123
        const val RADIO_ADDRESS = "xAA:BB:CC:DD:EE:FF"
    }
}
