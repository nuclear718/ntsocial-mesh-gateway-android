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
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelReadbackCompletion
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelReliabilityManagerImplTest {
    @Test
    fun `queue failure after begin attempts cleanup commit and never verifies`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome() // begin
        fixture.commandSender.outcomes += AdminOutcome(queued = false) // first channel
        fixture.commandSender.outcomes += AdminOutcome() // cleanup commit

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
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
    fun `routing timeout is unconfirmed rather than a radio rejection`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome(emitRoutingResponse = false)

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
        assertEquals(listOf("begin"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `unconfirmed normal commit is retried only as cleanup and remains unavailable`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.outcomes += AdminOutcome() // begin
        fixture.commandSender.outcomes += AdminOutcome() // primary
        fixture.commandSender.outcomes += AdminOutcome() // secondary
        fixture.commandSender.outcomes += AdminOutcome(queued = false) // normal commit
        fixture.commandSender.outcomes += AdminOutcome() // cleanup commit

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
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
    fun `commit ACK followed by reboot readback timeout remains pending`() = runTest {
        val fixture = Fixture(backgroundScope)

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFICATION_PENDING, result)
        assertNotEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(1, fixture.readbackRequests)
        assertEquals(listOf("invalidate", "command", "command", "command", "command"), fixture.gatewayLifecycle)
    }

    @Test
    fun `all ACKs and an exact fresh readback are required for verified`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.protectedSet

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(1, fixture.readbackRequests)
        assertEquals(
            listOf("invalidate", "command", "command", "command", "command", "activate"),
            fixture.gatewayLifecycle,
        )
    }

    @Test
    fun `channel-only apply preserves current LoRa without a config write`() = runTest {
        val fixture = Fixture(backgroundScope)
        val channelOnlySet =
            ChannelSet(settings = listOf(fixture.primary, fixture.changedSecondary), lora_config = null)
        fixture.nextReadback = fixture.channelSet(fixture.primary, fixture.changedSecondary)

        val result = fixture.manager.applyAndVerify(channelOnlySet)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertTrue("config" !in fixture.commandSender.events())
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `channel-only apply never writes a stale LoRa value when the config flow changes`() = runTest {
        val fixture = Fixture(backgroundScope)
        val changedLora = fixture.lora.copy(hop_limit = fixture.lora.hop_limit + 1)
        val channelOnlySet =
            ChannelSet(settings = listOf(fixture.primary, fixture.changedSecondary), lora_config = null)
        fixture.commandSender.afterMessageSent = { message ->
            if (message.set_channel?.index == 0) {
                fixture.localConfigFlow.value = LocalConfig(lora = changedLora)
            }
        }
        fixture.nextReadback =
            ChannelSet(settings = listOf(fixture.primary, fixture.changedSecondary), lora_config = changedLora)

        val result = fixture.manager.applyAndVerify(channelOnlySet)

        assertEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertTrue("config" !in fixture.commandSender.events())
        assertTrue("activate" !in fixture.gatewayLifecycle)
    }

    @Test
    fun `explicit LoRa replacement still writes config and verifies`() = runTest {
        val fixture = Fixture(backgroundScope)
        val changedLora = fixture.lora.copy(hop_limit = fixture.lora.hop_limit + 1)
        val replacement =
            ChannelSet(settings = listOf(fixture.primary, fixture.changedSecondary), lora_config = changedLora)
        fixture.nextReadback = replacement

        val result = fixture.manager.applyAndVerify(replacement)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "config", "commit"), fixture.commandSender.events())
        assertEquals(changedLora, fixture.commandSender.messages.single { it.set_config != null }.set_config?.lora)
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `ambiguous manual apply closes gateway before begin and never reactivates`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.channelSet(fixture.primary, fixture.changedSecondary)

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertEquals("invalidate", fixture.gatewayLifecycle.first())
        assertTrue("command" in fixture.gatewayLifecycle)
        assertTrue("activate" !in fixture.gatewayLifecycle)
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

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
        assertEquals(listOf("begin", "channel:0"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `same-address reconnect during writes cannot mutate the replacement session`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.afterMessageSent = { message ->
            if (message.set_channel?.index == 0) fixture.reconnectSameRadio()
        }

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
        assertEquals(listOf("begin", "channel:0"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `same-address reconnect after precheck rejects command at session-bound admission`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.commandSender.beforeSessionAdmission = { message ->
            if (message.set_channel?.index == 0) fixture.reconnectSameRadio()
        }

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
        assertEquals(listOf("begin"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
    }

    @Test
    fun `radio switch after fresh readback cannot return verified`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.protectedSet
        fixture.afterReadbackRequested = fixture::switchRadio

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFICATION_PENDING, result)
        assertNotEquals(ChannelReliabilityResult.VERIFIED, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(1, fixture.readbackRequests)
    }

    @Test
    fun `reconnect during stable readback capture after exact transaction remains pending`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.protectedSet
        fixture.beforeStableReadbackCapture = fixture::reconnectSameRadio

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFICATION_PENDING, result)
        assertNotEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertEquals(1, fixture.readbackRequests)
        assertTrue("activate" !in fixture.gatewayLifecycle)
    }

    @Test
    fun `same-address reconnect before readback admission cannot request replacement radio`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.nextReadback = fixture.protectedSet
        fixture.beforeReadbackAdmission = fixture::reconnectSameRadio

        val result = fixture.manager.applyAndVerify(fixture.protectedSet)

        assertEquals(ChannelReliabilityResult.VERIFICATION_PENDING, result)
        assertEquals(listOf("begin", "channel:0", "channel:1", "commit"), fixture.commandSender.events())
        assertEquals(0, fixture.readbackRequests)
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
        assertEquals(listOf("invalidate", "command", "command", "command", "activate"), fixture.gatewayLifecycle)
    }

    @Test
    fun `protected apply that reconnects during snapshot save restores previous snapshot`() = runTest {
        val fixture = Fixture(backgroundScope)
        val previous =
            ChannelProtectionSnapshot(
                maxChannels = fixture.maxChannels,
                channelSet = fixture.channelSet(fixture.primary, fixture.changedSecondary),
            )
        fixture.snapshotRepository.save(fixture.identity, previous)
        fixture.nextReadback = fixture.protectedSet
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        fixture.snapshotRepository.gateNextSave(saveStarted, releaseSave)

        val result = async { fixture.manager.applyAndVerify(fixture.protectedSet) }
        saveStarted.await()
        fixture.reconnectSameRadio()
        releaseSave.complete(Unit)

        assertEquals(ChannelReliabilityResult.VERIFICATION_PENDING, result.await())
        assertEquals(previous, fixture.snapshotRepository.get(fixture.identity))
        assertTrue("activate" !in fixture.gatewayLifecycle)
    }

    @Test
    fun `protect that reconnects during snapshot save removes stale new snapshot`() = runTest {
        val fixture = Fixture(backgroundScope)
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        fixture.snapshotRepository.gateNextSave(saveStarted, releaseSave)

        val result = async { fixture.manager.protectCurrentChannelSet() }
        saveStarted.await()
        fixture.reconnectSameRadio()
        releaseSave.complete(Unit)

        assertEquals(ChannelReliabilityResult.READBACK_FAILED, result.await())
        assertNull(fixture.snapshotRepository.get(fixture.identity))
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
        val readbackCompletion = MutableStateFlow<ChannelReadbackCompletion?>(null)
        val localConfigFlow = MutableStateFlow(LocalConfig(lora = lora))
        val radioSessionState = MutableStateFlow(readySession(epoch = 1, address = RADIO_A))
        val gatewayLifecycle = mutableListOf<String>()
        val commandSender = RecordingCommandSender(meshPackets) { gatewayLifecycle += "command" }
        val snapshotRepository = InMemoryChannelSnapshotRepository()
        var nextReadback: ChannelSet? = null
        var beforeReadbackAdmission: (() -> Unit)? = null
        var afterReadbackRequested: (() -> Unit)? = null
        var beforeStableReadbackCapture: (() -> Unit)? = null
        var readbackRequests = 0
        private var nextReadbackToken = 0L

        private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
        private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
        private val nodeRepository = FakeNodeRepository()
        private val ensureSession = mock<EnsureRemoteAdminSessionUseCase>(MockMode.autofill)
        private val configFlowManager = mock<MeshConfigFlowManager>(MockMode.autofill)
        private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
        private val ntsocialGatewayRepository = mock<NtsocialGatewayRepository>(MockMode.autofill)

        val manager: ChannelReliabilityManagerImpl

        init {
            every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Connected)
            every { serviceRepository.meshPacketFlow } returns meshPackets
            every { radioConfigRepository.channelSetFlow } returns
                flow {
                    beforeStableReadbackCapture?.also { callback ->
                        beforeStableReadbackCapture = null
                        callback()
                    }
                    emit(channelSetFlow.value)
                }
            every { radioConfigRepository.channelReadbackGeneration } returns readbackGeneration
            every { radioConfigRepository.localConfigFlow } returns localConfigFlow
            every { radioInterfaceService.radioSessionState } returns radioSessionState
            every { ntsocialGatewayRepository.invalidateInboundSession() } calls { gatewayLifecycle += "invalidate" }
            everySuspend { ntsocialGatewayRepository.activateInboundSession(any()) } calls
                {
                    gatewayLifecycle += "activate"
                    true
                }
            commandSender.currentRadioSessionEpoch = { radioSessionState.value.epoch }
            everySuspend { ensureSession(any()) } returns EnsureSessionResult.AlreadyActive
            everySuspend { ensureSession(any(), any()) } returns EnsureSessionResult.AlreadyActive
            every { configFlowManager.channelReadbackCompletion } returns readbackCompletion
            every { configFlowManager.beginChannelReadbackForSession(any()) } calls
                { call ->
                    beforeReadbackAdmission?.invoke()
                    if (radioSessionState.value.epoch != call.arg<Long>(0)) {
                        null
                    } else {
                        readbackRequests++
                        val token = ++nextReadbackToken
                        nextReadback?.let { readback ->
                            channelSetFlow.value = readback
                            readbackGeneration.value += 1
                            readbackCompletion.value =
                                ChannelReadbackCompletion(token, radioSessionState.value.epoch, readback)
                        }
                        afterReadbackRequested?.invoke()
                        token
                    }
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
                    mutationLock = ChannelMutationLock(),
                    radioInterfaceService = radioInterfaceService,
                    ntsocialGatewayRepository = ntsocialGatewayRepository,
                    serviceScope = scope,
                )
        }

        fun channelSet(vararg settings: ChannelSettings): ChannelSet =
            ChannelSet(settings = settings.toList(), lora_config = lora)

        fun switchRadio() {
            radioSessionState.value = readySession(epoch = radioSessionState.value.epoch + 1, address = RADIO_B)
            nodeRepository.setMyNodeInfo(
                TestDataFactory.createMyNodeInfo(myNodeNum = NODE_NUM + 1)
                    .copy(maxChannels = maxChannels, deviceId = "8899AABBCCDDEEFF"),
            )
        }

        fun reconnectSameRadio() {
            radioSessionState.value =
                readySession(
                    epoch = radioSessionState.value.epoch + 1,
                    address = radioSessionState.value.selectedDeviceAddress!!,
                )
        }

        private fun channel(name: String, psk: String): ChannelSettings =
            ChannelSettings(name = name, psk = psk.decodeHex())

        private fun readySession(epoch: Long, address: String): RadioSessionState = RadioSessionState(
            epoch = epoch,
            selectedDeviceAddress = address,
            activeDeviceAddress = address,
            transportConnectionState = ConnectionState.Connected,
            configured = true,
        )

        private companion object {
            const val RADIO_A = "radio-a"
            const val RADIO_B = "radio-b"
        }
    }

    private data class AdminOutcome(
        val queued: Boolean = true,
        val routingError: Routing.Error = Routing.Error.NONE,
        val unrelatedRoutingError: Routing.Error? = null,
        val emitRoutingResponse: Boolean = true,
    )

    private class RecordingCommandSender(
        private val meshPackets: MutableSharedFlow<MeshPacket>,
        private val onMessageSent: () -> Unit,
    ) : CommandSender {
        val messages = mutableListOf<AdminMessage>()
        val outcomes = ArrayDeque<AdminOutcome>()
        var afterMessageSent: ((AdminMessage) -> Unit)? = null
        var beforeSessionAdmission: ((AdminMessage) -> Unit)? = null
        var currentRadioSessionEpoch: () -> Long = { 0L }
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
            if (outcome.queued && outcome.emitRoutingResponse) {
                outcome.unrelatedRoutingError?.let { error ->
                    meshPackets.emit(routingPacket(from = destNum + 1, requestId = requestId, error = error))
                }
                meshPackets.emit(routingPacket(from = destNum, requestId = requestId, error = outcome.routingError))
            }
            afterMessageSent?.invoke(messages.last())
            onMessageSent()
            return outcome.queued
        }

        override suspend fun sendAdminAwaitForSession(
            expectedRadioSessionEpoch: Long,
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean {
            val message = initFn()
            beforeSessionAdmission?.invoke(message)
            if (currentRadioSessionEpoch() != expectedRadioSessionEpoch) return false
            return sendAdminAwait(destNum, requestId, wantResponse) { message }
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
        private var nextSaveStarted: CompletableDeferred<Unit>? = null
        private var nextSaveRelease: CompletableDeferred<Unit>? = null

        override suspend fun get(stableDeviceIdentity: String): ChannelProtectionSnapshot? =
            snapshots[stableDeviceIdentity]

        override suspend fun save(stableDeviceIdentity: String, snapshot: ChannelProtectionSnapshot) {
            val started = nextSaveStarted
            val release = nextSaveRelease
            nextSaveStarted = null
            nextSaveRelease = null
            started?.complete(Unit)
            release?.await()
            snapshots[stableDeviceIdentity] = snapshot
        }

        override suspend fun clear(stableDeviceIdentity: String) {
            snapshots.remove(stableDeviceIdentity)
        }

        fun gateNextSave(started: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>) {
            nextSaveStarted = started
            nextSaveRelease = release
        }
    }

    private companion object {
        const val NODE_NUM = 123
    }
}
