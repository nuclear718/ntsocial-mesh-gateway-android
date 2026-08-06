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
package com.ntsocial.meshlink.core.domain.usecase.settings

import com.ntsocial.meshlink.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import com.ntsocial.meshlink.core.domain.usecase.session.EnsureSessionResult
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.TestDataFactory
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChannelReliabilityManagerImplTest {
    @Test
    fun `queue failure after begin attempts cleanup commit and never verifies`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome() // begin
        fixture.commandSender.outcomes += AdminOutcome(queued = false) // first channel
        fixture.commandSender.outcomes += AdminOutcome() // cleanup commit

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.RADIO_REJECTED, result)
        assertEquals(listOf("begin", "channel:0", "commit"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `routing NAK after begin attempts cleanup commit and never verifies`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome() // begin
        fixture.commandSender.outcomes += AdminOutcome(routingError = Routing.Error.NO_ROUTE) // first channel
        fixture.commandSender.outcomes += AdminOutcome() // cleanup commit

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.RADIO_REJECTED, result)
        assertEquals(listOf("begin", "channel:0", "commit"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `failed normal commit is retried only as cleanup and remains rejected`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome() // begin
        fixture.commandSender.outcomes += AdminOutcome() // primary
        fixture.commandSender.outcomes += AdminOutcome() // secondary
        fixture.commandSender.outcomes += AdminOutcome(queued = false) // normal commit
        fixture.commandSender.outcomes += AdminOutcome() // cleanup commit

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.RADIO_REJECTED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit", "commit"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `all ACKs with mismatched fresh readback cannot return verified`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.channelSet(fixture.primary, fixture.changedSecondary)

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertNotEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(1, fixture.commandSender.messages.count { it.commit_edit_settings == true })
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `all ACKs and an exact fresh readback are required for verified`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.protectedSet

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `unrelated sender NAK cannot terminate the matching ACK wait`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome(unrelatedRoutingError = Routing.Error.NO_ROUTE)
        fixture.nextReadback = fixture.protectedSet

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
    }

    @Test
    fun `radio switch during writes stops before the next command and skips cleanup`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.afterMessageSent = { message ->
            if (message.set_channel?.index == 0) fixture.switchRadio()
        }

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.RADIO_REJECTED, result)
        assertEquals(listOf("begin", "channel:0"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `radio switch after fresh readback cannot return verified`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.protectedSet
        fixture.afterReadbackRequested = fixture::switchRadio

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertNotEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `missing secondary repairs only that slot and requires exact readback`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.snapshotRepository.save(
            fixture.identity,
            ChannelProtectionSnapshot(maxChannels = fixture.maxChannels, channelSet = fixture.protectedSet),
        )
        fixture.channelSetFlow.value = fixture.channelSet(fixture.primary)
        fixture.nextReadback = fixture.protectedSet

        val result = fixture.manager.reconcileProtectedChannelSet()

        assertEquals(ChannelReliabilityResult.REPAIRED, result)
        assertEquals(listOf("begin", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(fixture.secondary, assertNotNull(fixture.commandSender.messages[1].set_channel).settings)
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `conflicting primary does not open an edit transaction`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.snapshotRepository.save(
            fixture.identity,
            ChannelProtectionSnapshot(maxChannels = fixture.maxChannels, channelSet = fixture.protectedSet),
        )
        fixture.channelSetFlow.value = fixture.channelSet(fixture.changedPrimary, fixture.secondary)

        val result = fixture.manager.reconcileProtectedChannelSet()

        assertEquals(ChannelReliabilityResult.CONFLICT, result)
        assertTrue(fixture.commandSender.messages.isEmpty())
        assertEquals(0, fixture.readbackRequests)
    }

    private class Fixture(scope: CoroutineScope) {
        val identity = "0011223344556677"
        val maxChannels = 2
        val lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW)
        val primary = channel("primary", "01")
        val secondary = channel("secondary", "02")
        val changedPrimary = channel("changed-primary", "03")
        val changedSecondary = channel("changed-secondary", "04")
        val protectedSet = channelSet(primary, secondary)

        val meshPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 16)
        val channelSetFlow = MutableStateFlow(protectedSet)
        val readbackGeneration = MutableStateFlow(1L)
        val localConfigFlow = MutableStateFlow(LocalConfig(lora = lora))
        val commandSender = RecordingCommandSender(meshPackets)
        val snapshotRepository = InMemoryChannelSnapshotRepository()
        var nextReadback: ChannelSet? = null
        var afterReadbackRequested: (() -> Unit)? = null
        var readbackRequests = 0

        private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
        private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        private val nodeRepository = FakeNodeRepository()
        private val ensureSession = mock<EnsureRemoteAdminSessionUseCase>(MockMode.autofill)
        private val configFlowManager = mock<MeshConfigFlowManager>(MockMode.autofill)

        val manager: ChannelReliabilityManagerImpl

        init {
            every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Connected)
            every { serviceRepository.meshPacketFlow } returns meshPackets
            every { radioConfigRepository.channelSetFlow } returns channelSetFlow
            every { radioConfigRepository.channelReadbackGeneration } returns readbackGeneration
            every { radioConfigRepository.localConfigFlow } returns localConfigFlow
            everySuspend { ensureSession(any()) } returns EnsureSessionResult.AlreadyActive
            every { configFlowManager.triggerWantConfig() } calls
                {
                    readbackRequests++
                    nextReadback?.let { readback ->
                        channelSetFlow.value = readback
                        readbackGeneration.value += 1
                    }
                    afterReadbackRequested?.invoke()
                }
            nodeRepository.setMyNodeInfo(
                TestDataFactory.createMyNodeInfo(myNodeNum = NODE_NUM)
                    .copy(maxChannels = maxChannels, deviceId = identity),
            )
            manager =
                ChannelReliabilityManagerImpl(
                    commandSender = commandSender,
                    serviceRepository = serviceRepository,
                    nodeRepository = nodeRepository,
                    radioConfigRepository = radioConfigRepository,
                    channelSnapshotRepository = snapshotRepository,
                    ensureRemoteAdminSession = ensureSession,
                    meshConfigFlowManager = lazy { configFlowManager },
                    operationLock = ChannelOperationLock(),
                    serviceScope = scope,
                )
        }

        fun channelSet(vararg settings: ChannelSettings): ChannelSet =
            ChannelSet(settings = settings.toList(), lora_config = lora)

        fun switchRadio() {
            nodeRepository.setMyNodeInfo(
                TestDataFactory.createMyNodeInfo(myNodeNum = NODE_NUM + 1)
                    .copy(maxChannels = maxChannels, deviceId = "8899AABBCCDDEEFF"),
            )
        }

        private fun channel(name: String, psk: String): ChannelSettings =
            ChannelSettings(name = name, psk = psk.decodeHex())
    }

    private data class AdminOutcome(
        val queued: Boolean = true,
        val routingError: Routing.Error = Routing.Error.NONE,
        val unrelatedRoutingError: Routing.Error? = null,
    )

    private class RecordingCommandSender(private val meshPackets: MutableSharedFlow<MeshPacket>) : CommandSender {
        val messages = mutableListOf<AdminMessage>()
        val outcomes = ArrayDeque<AdminOutcome>()
        var afterMessageSent: ((AdminMessage) -> Unit)? = null
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
        ): Boolean {
            messages += initFn()
            val outcome = outcomes.removeFirstOrNull() ?: AdminOutcome()
            if (outcome.queued) {
                outcome.unrelatedRoutingError?.let { error ->
                    meshPackets.emit(routingPacket(from = destNum + 1, requestId = requestId, error = error))
                }
                meshPackets.emit(routingPacket(from = destNum, requestId = requestId, error = outcome.routingError))
            }
            afterMessageSent?.invoke(messages.last())
            return outcome.queued
        }

        private fun routingPacket(from: Int, requestId: Int, error: Routing.Error): MeshPacket = MeshPacket(
            from = from,
            decoded =
            Data(
                portnum = PortNum.ROUTING_APP,
                request_id = requestId,
                payload = Routing(error_reason = error).encode().toByteString(),
            ),
        )

        override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) = Unit

        override fun requestPosition(destNum: Int, currentPosition: Position) = Unit

        override fun setFixedPosition(destNum: Int, pos: Position) = Unit

        override fun requestUserInfo(destNum: Int) = Unit

        override fun requestTraceroute(requestId: Int, destNum: Int) = Unit

        override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) = Unit

        override fun requestNeighborInfo(requestId: Int, destNum: Int) = Unit

        fun events(): List<String> = messages.map { message ->
            when {
                message.begin_edit_settings == true -> "begin"
                message.set_channel != null -> "channel:${message.set_channel!!.index}"
                message.set_config != null -> "config"
                message.commit_edit_settings == true -> "commit"
                else -> "other"
            }
        }
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

    private companion object {
        const val NODE_NUM = 123
    }
}
