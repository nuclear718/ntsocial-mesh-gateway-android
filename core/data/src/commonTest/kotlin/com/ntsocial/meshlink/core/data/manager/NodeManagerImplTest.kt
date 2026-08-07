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

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.repository.CurrentNodeSnapshot
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NotificationManager
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

class NodeManagerImplTest {

    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val serviceBroadcasts: ServiceBroadcasts = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val testScope = TestScope()
    private lateinit var ingressWorkTracker: RadioIngressWorkTracker

    private lateinit var nodeManager: NodeManagerImpl

    @BeforeTest
    fun setUp() {
        ingressWorkTracker = RadioIngressWorkTracker()
        nodeManager =
            NodeManagerImpl(nodeRepository, serviceBroadcasts, notificationManager, ingressWorkTracker, testScope)
    }

    @Test
    fun `getOrCreateNode creates default user for unknown node`() {
        val nodeNum = 1234
        val result = nodeManager.getOrCreateNode(nodeNum)

        assertNotNull(result)
        assertEquals(nodeNum, result.num)
        assertTrue(result.user.long_name.startsWith("Meshtastic"))
        assertEquals(DataPacket.nodeNumToDefaultId(nodeNum), result.user.id)
    }

    @Test
    fun `awaited cache load ignores replayed old state and waits for direct active database snapshot`() = runTest {
        val oldNode = Node(num = 1, user = User(id = "!00000001", long_name = "old"))
        val newNode = Node(num = 2, user = User(id = "!00000002", long_name = "new"))
        val newMyInfo = com.ntsocial.meshlink.core.testing.TestDataFactory.createMyNodeInfo(myNodeNum = newNode.num)
        val directSnapshotGate = CompletableDeferred<Unit>()
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(oldNode.num to oldNode))
        everySuspend { nodeRepository.readCurrentNodeSnapshot() } calls
            {
                directSnapshotGate.await()
                CurrentNodeSnapshot(nodesByNumber = mapOf(newNode.num to newNode), myNodeInfo = newMyInfo)
            }

        val load = async { nodeManager.loadCachedNodeDBAndAwait() }
        runCurrent()

        assertFalse(load.isCompleted)
        assertEquals(emptyMap(), nodeManager.nodeDBbyNodeNum)

        directSnapshotGate.complete(Unit)
        load.await()

        assertEquals(mapOf(newNode.num to newNode), nodeManager.nodeDBbyNodeNum)
        assertEquals(newNode.num, nodeManager.myNodeNum.value)
    }

    @Test
    fun `user-owned node update still persists while radio ingress is paused`() = testScope.runTest {
        nodeManager.setNodeDbReady(true)
        ingressWorkTracker.pauseAndAwaitRetiredWork()

        nodeManager.updateNode(42) { it.copy(isFavorite = true) }
        runCurrent()

        verifySuspend { nodeRepository.upsert(any()) }
    }

    @Test
    fun `radio-owned node update is rejected while retired ingress is paused`() = testScope.runTest {
        nodeManager.setNodeDbReady(true)
        ingressWorkTracker.pauseAndAwaitRetiredWork()

        nodeManager.updateNodeFromRadio(43) { it.copy(lastHeard = 99) }
        runCurrent()

        verifySuspend(mode = VerifyMode.not) { nodeRepository.upsert(any()) }
    }

    @Test
    fun `handleReceivedUser preserves existing user if incoming is default`() {
        val nodeNum = 1234
        val existingUser =
            User(id = "!12345678", long_name = "My Custom Name", short_name = "MCN", hw_model = HardwareModel.TLORA_V2)

        // Setup existing node
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingDefaultUser =
            User(id = "!12345678", long_name = "Meshtastic 5678", short_name = "5678", hw_model = HardwareModel.UNSET)

        nodeManager.handleReceivedUser(nodeNum, incomingDefaultUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("My Custom Name", result!!.user.long_name)
        assertEquals(HardwareModel.TLORA_V2, result.user.hw_model)
    }

    @Test
    fun `handleReceivedUser updates user if incoming is higher detail`() {
        val nodeNum = 1234
        // Use a non-UNSET hw_model so isUnknownUser=false (avoids new-node notification + getString)
        val existingUser =
            User(id = "!12345678", long_name = "Old Name", short_name = "ON", hw_model = HardwareModel.TLORA_V2)

        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingDetailedUser =
            User(id = "!12345678", long_name = "Real User", short_name = "RU", hw_model = HardwareModel.TLORA_V1)

        nodeManager.handleReceivedUser(nodeNum, incomingDetailedUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("Real User", result!!.user.long_name)
        assertEquals(HardwareModel.TLORA_V1, result.user.hw_model)
    }

    @Test
    fun `handleReceivedPosition updates node position`() {
        val nodeNum = 1234
        val position = ProtoPosition(latitude_i = 450000000, longitude_i = 900000000)

        nodeManager.handleReceivedPosition(nodeNum, 9999, position, 0)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result)
        assertNotNull(result.position)
        assertEquals(450000000, result.position.latitude_i)
        assertEquals(900000000, result.position.longitude_i)
    }

    @Test
    fun `handleReceivedPosition with zero coordinates preserves last known location but updates satellites`() {
        val nodeNum = 1234
        val initialPosition = ProtoPosition(latitude_i = 450000000, longitude_i = 900000000, sats_in_view = 10)
        nodeManager.handleReceivedPosition(nodeNum, 9999, initialPosition, 1000000L)

        // Receive "zero" position with new satellite count
        val zeroPosition = ProtoPosition(latitude_i = 0, longitude_i = 0, sats_in_view = 5, time = 1001)
        nodeManager.handleReceivedPosition(nodeNum, 9999, zeroPosition, 1001000L)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(450000000, result!!.position.latitude_i)
        assertEquals(900000000, result.position.longitude_i)
        assertEquals(5, result.position.sats_in_view)
        assertEquals(1001, result.lastHeard)
    }

    @Test
    fun `handleReceivedPosition for local node ignores purely empty packets`() {
        val myNum = 1111
        val emptyPos = ProtoPosition(latitude_i = 0, longitude_i = 0, sats_in_view = 0, time = 0)

        nodeManager.handleReceivedPosition(myNum, myNum, emptyPos, 0)

        val result = nodeManager.nodeDBbyNodeNum[myNum]
        // Should still be null since the empty position for local node is ignored
        assertNull(result)
    }

    @Test
    fun `handleReceivedTelemetry updates lastHeard`() {
        val nodeNum = 1234
        nodeManager.updateNode(nodeNum) { it.copy(lastHeard = 1000) }

        val telemetry = Telemetry(time = 2000, device_metrics = DeviceMetrics(battery_level = 50))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(2000, result!!.lastHeard)
    }

    @Test
    fun `handleReceivedTelemetry updates device metrics`() {
        val nodeNum = 1234
        val telemetry = Telemetry(device_metrics = DeviceMetrics(battery_level = 75, voltage = 3.8f))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result!!.deviceMetrics)
        assertEquals(75, result.deviceMetrics.battery_level)
        assertEquals(3.8f, result.deviceMetrics.voltage)
    }

    @Test
    fun `handleReceivedTelemetry updates environment metrics`() {
        val nodeNum = 1234
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(temperature = 22.5f, relative_humidity = 45.0f))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result!!.environmentMetrics)
        assertEquals(22.5f, result.environmentMetrics.temperature)
        assertEquals(45.0f, result.environmentMetrics.relative_humidity)
    }

    @Test
    fun `handleReceivedTelemetry updates air quality metrics`() {
        val nodeNum = 1234
        val telemetry =
            Telemetry(air_quality_metrics = AirQualityMetrics(pm10_standard = 1, pm25_standard = 2, co2 = 615))

        nodeManager.handleReceivedTelemetry(nodeNum, telemetry)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals(1, result!!.airQualityMetrics.pm10_standard)
        assertEquals(2, result.airQualityMetrics.pm25_standard)
        assertEquals(615, result.airQualityMetrics.co2)
    }

    @Test
    fun `clear resets internal state`() {
        nodeManager.updateNode(1234) { it.copy(user = it.user.copy(long_name = "Test")) }
        nodeManager.clear()

        assertTrue(nodeManager.nodeDBbyNodeNum.isEmpty())
        assertTrue(nodeManager.nodeDBbyID.isEmpty())
        assertNull(nodeManager.myNodeNum.value)
    }

    @Test
    fun `toNodeID returns broadcast ID for broadcast nodeNum`() {
        val result = nodeManager.toNodeID(DataPacket.NODENUM_BROADCAST)
        assertEquals(DataPacket.ID_BROADCAST, result)
    }

    @Test
    fun `toNodeID returns default hex ID for unknown node`() {
        val result = nodeManager.toNodeID(0x1234)
        assertEquals(DataPacket.nodeNumToDefaultId(0x1234), result)
    }

    @Test
    fun `toNodeID returns user ID for known node`() {
        val nodeNum = 5678
        val userId = "!customid"
        nodeManager.updateNode(nodeNum) { it.copy(user = it.user.copy(id = userId)) }
        val result = nodeManager.toNodeID(nodeNum)
        assertEquals(userId, result)
    }

    @Test
    fun `removeByNodenum removes node from both maps`() {
        val nodeNum = 1234
        nodeManager.updateNode(nodeNum) {
            Node(num = nodeNum, user = User(id = "!testnode", long_name = "Test", short_name = "T"))
        }
        assertTrue(nodeManager.nodeDBbyNodeNum.containsKey(nodeNum))
        assertTrue(nodeManager.nodeDBbyID.containsKey("!testnode"))

        nodeManager.removeByNodenum(nodeNum)

        assertTrue(!nodeManager.nodeDBbyNodeNum.containsKey(nodeNum))
        assertTrue(!nodeManager.nodeDBbyID.containsKey("!testnode"))
    }

    @Test
    fun `handleReceivedUser sets publicKey from user public_key`() {
        val nodeNum = 1234
        val pk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val existingUser =
            User(id = "!12345678", long_name = "Existing", short_name = "EX", hw_model = HardwareModel.TLORA_V2)
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingUser =
            User(
                id = "!12345678",
                long_name = "Updated",
                short_name = "UP",
                hw_model = HardwareModel.TLORA_V2,
                public_key = pk,
            )
        nodeManager.handleReceivedUser(nodeNum, incomingUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertEquals(pk, result.publicKey)
        assertEquals(pk, result.user.public_key)
        assertTrue(result.hasPKC)
    }

    @Test
    fun `handleReceivedUser sets empty publicKey when key mismatch clears user key`() {
        val nodeNum = 1234
        val existingPk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val existingUser =
            User(
                id = "!12345678",
                long_name = "Existing",
                short_name = "EX",
                hw_model = HardwareModel.TLORA_V2,
                public_key = existingPk,
            )
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser, publicKey = existingPk) }

        val differentPk = ByteArray(32) { (it + 10).toByte() }.toByteString()
        val incomingUser =
            User(
                id = "!12345678",
                long_name = "Updated",
                short_name = "UP",
                hw_model = HardwareModel.TLORA_V2,
                public_key = differentPk,
            )
        nodeManager.handleReceivedUser(nodeNum, incomingUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        // Key mismatch: newUser gets public_key cleared to EMPTY, and publicKey should match
        assertEquals(ByteString.EMPTY, result.publicKey)
        assertEquals(ByteString.EMPTY, result.user.public_key)
    }

    @Test
    fun `installNodeInfo sets publicKey from user public_key`() {
        val nodeNum = 5678
        val pk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val user =
            User(
                id = "!abcd1234",
                long_name = "Remote Node",
                short_name = "RN",
                hw_model = HardwareModel.HELTEC_V3,
                public_key = pk,
            )
        val info = ProtoNodeInfo(num = nodeNum, user = user, last_heard = 1000, channel = 0)

        nodeManager.installNodeInfo(info)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertEquals(pk, result.publicKey)
        assertEquals(pk, result.user.public_key)
        assertTrue(result.hasPKC)
    }

    @Test
    fun `installNodeInfo clears publicKey for licensed users`() {
        val nodeNum = 5678
        val pk = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val user =
            User(
                id = "!abcd1234",
                long_name = "Licensed Op",
                short_name = "LO",
                hw_model = HardwareModel.HELTEC_V3,
                public_key = pk,
                is_licensed = true,
            )
        val info = ProtoNodeInfo(num = nodeNum, user = user, last_heard = 1000, channel = 0)

        nodeManager.installNodeInfo(info)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertEquals(ByteString.EMPTY, result.publicKey)
        assertEquals(ByteString.EMPTY, result.user.public_key)
    }

    @Test
    fun `installNodeInfo records XEdDSA signing support`() {
        val nodeNum = 5678
        val user =
            User(id = "!abcd1234", long_name = "Signed Node", short_name = "SN", hw_model = HardwareModel.HELTEC_V3)
        val info = ProtoNodeInfo(num = nodeNum, user = user, last_heard = 1000, channel = 0, has_xeddsa_signed = true)

        nodeManager.installNodeInfo(info)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]!!
        assertTrue(result.signsPackets)
    }
}
