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
package com.ntsocial.meshlink.core.data.ntsocial

import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.model.SessionStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannel
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import com.ntsocial.meshlink.core.testing.FakeRadioConfigRepository
import com.ntsocial.meshlink.core.testing.FakeRadioInterfaceService
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock

class NtsocialChannelProvisionerTest {

    @Test
    fun `stale handshake session cannot provision after acquiring channel lock`() = runTest {
        val fixture = fixture()

        val result =
            fixture.provisioner.ensureDefaultChannelForSession(
                myNodeNum = MY_NODE_NUM,
                maxChannels = 8,
                expectedRadioSessionEpoch = 99,
            )

        assertNull(result)
        assertEquals(emptyList(), fixture.commandSender.events)
    }

    @Test
    fun `same-address reconnect before admin admission cannot provision replacement session`() = runTest {
        val fixture = fixture()
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"))))
        fixture.repository.setLocalConfigDirect(
            LocalConfig(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.UNSET)),
        )
        val expectedEpoch = fixture.radioInterfaceService.radioSessionState.value.epoch
        fixture.commandSender.beforeSessionAdmission = { message ->
            if (message.set_config != null) fixture.reconnectSameRadio()
        }

        val result =
            fixture.provisioner.ensureDefaultChannelForSession(
                myNodeNum = MY_NODE_NUM,
                maxChannels = 8,
                expectedRadioSessionEpoch = expectedEpoch,
            )

        assertNull(result)
        assertEquals(listOf("set_config"), fixture.commandSender.sessionAdmissionAttempts)
        assertEquals(emptyList(), fixture.commandSender.events)
        assertEquals(null, fixture.repository.lastSetLocalConfig)
        assertEquals(emptyList(), fixture.commandSender.setChannels)
    }

    @Test
    fun `preserves p32 when canonical NTsocial channel already exists`() = runTest {
        val fixture = fixture()
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(canonicalSettings), lora_config = defaultLora))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        assertEquals(NtsocialChannelProvisionResult.AlreadyPresent, result)
        assertEquals(32, fixture.repository.currentChannelSet.settings.single().positionPrecision)
        assertEquals(emptyList(), fixture.commandSender.events)
    }

    @Test
    fun `preserves p0 when other canonical NTsocial fields already match`() = runTest {
        val fixture = fixture()
        val existingNtsocial = canonicalSettings.withPositionPrecision(0)
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(existingNtsocial), lora_config = defaultLora))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        assertEquals(NtsocialChannelProvisionResult.AlreadyPresent, result)
        assertEquals(0, fixture.repository.currentChannelSet.settings.single().positionPrecision)
        assertEquals(emptyList(), fixture.commandSender.events)
    }

    @Test
    fun `preserves p0 while repairing other canonical NTsocial fields`() = runTest {
        val fixture = fixture()
        val existingNtsocial =
            canonicalSettings.copy(
                psk = ByteString.of(9),
                uplink_enabled = false,
                downlink_enabled = false,
                module_settings = ModuleSettings(position_precision = 0, is_muted = true),
            )
        val expected = canonicalSettings.withPositionPrecision(0)
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"), existingNtsocial)))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.UPDATED, provisioned.channelChange)
        assertEquals(1, provisioned.channelIndex)
        assertEquals(expected, fixture.repository.currentChannelSet.settings[1])
        assertEquals(expected, fixture.commandSender.setChannels.single().settings)
    }

    @Test
    fun `updates existing NTsocial-named slot to canonical settings`() = runTest {
        val fixture = fixture()
        val existingNtsocial =
            canonicalSettings.copy(psk = ByteString.of(9), uplink_enabled = false, downlink_enabled = false)
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"), existingNtsocial)))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.UPDATED, provisioned.channelChange)
        assertEquals(1, provisioned.channelIndex)
        assertEquals(canonicalSettings, fixture.repository.currentChannelSet.settings[1])
        assertEquals(Channel.Role.SECONDARY, fixture.commandSender.setChannels.single().role)
    }

    @Test
    fun `updates existing same-PSK slot to canonical settings`() = runTest {
        val fixture = fixture()
        val samePskWrongName = canonicalSettings.copy(name = "Other", uplink_enabled = false)
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"), samePskWrongName)))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.UPDATED, provisioned.channelChange)
        assertEquals(1, provisioned.channelIndex)
        assertEquals(canonicalSettings, fixture.repository.currentChannelSet.settings[1])
    }

    @Test
    fun `adds NTsocial into free secondary slot`() = runTest {
        val fixture = fixture()
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"))))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.ADDED, provisioned.channelChange)
        assertEquals(1, provisioned.channelIndex)
        assertEquals(canonicalSettings, fixture.repository.currentChannelSet.settings[1])
        assertEquals(Channel.Role.SECONDARY, fixture.commandSender.setChannels.single().role)
    }

    @Test
    fun `preserves every existing slot when channel slots are full`() = runTest {
        val fixture = fixture()
        val fullChannelSet = (0 until 8).map { index -> otherSettings("Channel$index") }
        fixture.repository.setChannelSet(ChannelSet(settings = fullChannelSet))
        fixture.repository.setLocalConfigDirect(
            LocalConfig(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.UNSET)),
        )

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        assertEquals(NtsocialChannelProvisionResult.NoSpace, result)
        assertEquals(fullChannelSet, fixture.repository.currentChannelSet.settings)
        assertEquals(emptyList(), fixture.commandSender.events)
        assertEquals(null, fixture.repository.lastSetLocalConfig)
        assertFalse(result.toDefaultChannelStatus(channelIndex = null).ready)
        assertEquals("NO_SPACE", result.toDefaultChannelStatus(channelIndex = null).provisioningState)
    }

    @Test
    fun `preserves primary when radio supports one full channel`() = runTest {
        val fixture = fixture()
        val primary = otherSettings("Primary")
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(primary)))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 1)

        assertEquals(NtsocialChannelProvisionResult.NoSpace, result)
        assertEquals(listOf(primary), fixture.repository.currentChannelSet.settings)
        assertEquals(emptyList(), fixture.commandSender.events)
    }

    @Test
    fun `applies LoRa config when current region is unset`() = runTest {
        val fixture = fixture()
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(canonicalSettings), lora_config = defaultLora))
        fixture.repository.setLocalConfigDirect(
            LocalConfig(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.UNSET)),
        )

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.NONE, provisioned.channelChange)
        assertEquals(true, provisioned.loraConfigApplied)
        assertEquals(defaultLora, fixture.repository.lastSetLocalConfig?.lora)
        assertEquals(listOf("set_config"), fixture.commandSender.events)
    }

    @Test
    fun `preserves already configured LoRa region`() = runTest {
        val fixture = fixture()
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"))))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.ADDED, provisioned.channelChange)
        assertFalse(provisioned.loraConfigApplied)
        assertEquals(null, fixture.repository.lastSetLocalConfig)
    }

    @Test
    fun `waits for local admin session refresh before writing channel`() = runTest {
        val fixture = fixture(sessionActive = false)
        fixture.repository.setChannelSet(ChannelSet(settings = listOf(otherSettings("Primary"))))
        fixture.repository.setLocalConfigDirect(configuredLocalConfig)

        val result = fixture.provisioner.ensureDefaultChannel(MY_NODE_NUM, maxChannels = 8)

        val provisioned = assertIs<NtsocialChannelProvisionResult.Provisioned>(result)
        assertEquals(NtsocialChannelChange.ADDED, provisioned.channelChange)
        assertEquals(listOf("metadata", "set_channel"), fixture.commandSender.events)
        assertFalse(fixture.commandSender.writeBeforeSession)
    }

    private fun fixture(sessionActive: Boolean = true): Fixture {
        val repository = FakeRadioConfigRepository()
        val sessionManager = FakeSessionManager(sessionActive)
        val commandSender = RecordingCommandSender(sessionManager)
        val gatewayRepository = mock<NtsocialGatewayRepository>(MockMode.autofill)
        val meshConfigFlowManager = mock<MeshConfigFlowManager>(MockMode.autofill)
        val radioInterfaceService = FakeRadioInterfaceService()
        radioInterfaceService.setDeviceAddress(RADIO_ADDRESS)
        radioInterfaceService.connect()
        radioInterfaceService.onConnect()
        check(radioInterfaceService.markCurrentSessionConfigured(radioInterfaceService.radioSessionState.value.epoch))
        commandSender.currentRadioSessionEpoch = { radioInterfaceService.radioSessionState.value.epoch }
        return Fixture(
            repository = repository,
            commandSender = commandSender,
            radioInterfaceService = radioInterfaceService,
            provisioner =
            NtsocialChannelProvisioner(
                commandSender,
                repository,
                sessionManager,
                ChannelOperationLock(),
                ChannelMutationLock(),
                radioInterfaceService,
                gatewayRepository,
                lazy { meshConfigFlowManager },
            ),
        )
    }

    private fun otherSettings(name: String): ChannelSettings =
        ChannelSettings(name = name, psk = ByteArray(32) { index -> (name.hashCode() + index).toByte() }.toByteString())

    private fun ChannelSettings.withPositionPrecision(positionPrecision: Int): ChannelSettings =
        copy(module_settings = (module_settings ?: ModuleSettings()).copy(position_precision = positionPrecision))

    private val ChannelSettings.positionPrecision: Int
        get() = module_settings?.position_precision ?: 0

    private data class Fixture(
        val repository: FakeRadioConfigRepository,
        val commandSender: RecordingCommandSender,
        val radioInterfaceService: FakeRadioInterfaceService,
        val provisioner: NtsocialChannelProvisioner,
    ) {
        fun reconnectSameRadio() {
            radioInterfaceService.onDisconnect(isPermanent = true, errorMessage = null)
            radioInterfaceService.onConnect()
            check(
                radioInterfaceService.markCurrentSessionConfigured(radioInterfaceService.radioSessionState.value.epoch),
            )
        }
    }

    private class FakeSessionManager(sessionActive: Boolean) : SessionManager {
        private val activeNodes = mutableSetOf<Int>()
        private val passkeys = mutableMapOf<Int, ByteString>()
        private val refreshEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)

        override val sessionRefreshFlow: Flow<Int> = refreshEvents

        init {
            if (sessionActive) recordSession(MY_NODE_NUM, PASSKEY)
        }

        override fun recordSession(srcNodeNum: Int, passkey: ByteString) {
            if (passkey == ByteString.EMPTY) return
            passkeys[srcNodeNum] = passkey
            activeNodes += srcNodeNum
            refreshEvents.tryEmit(srcNodeNum)
        }

        override fun getPasskey(destNum: Int): ByteString = passkeys[destNum] ?: ByteString.EMPTY

        override fun clearAll() {
            activeNodes.clear()
            passkeys.clear()
        }

        override fun observeSessionStatus(destNum: Int): Flow<SessionStatus> = flowOf(
            if (activeNodes.contains(destNum)) {
                SessionStatus.Active(Clock.System.now())
            } else {
                SessionStatus.NoSession
            },
        )

        fun hasActiveSession(destNum: Int): Boolean = activeNodes.contains(destNum)
    }

    private class RecordingCommandSender(private val sessionManager: FakeSessionManager) : CommandSender {
        val events = mutableListOf<String>()
        val setChannels = mutableListOf<Channel>()
        val sessionAdmissionAttempts = mutableListOf<String>()
        var beforeSessionAdmission: ((AdminMessage) -> Unit)? = null
        var currentRadioSessionEpoch: () -> Long = { 0L }
        var writeBeforeSession = false
            private set

        private var nextPacketId = 1

        override fun getCurrentPacketId(): Long = nextPacketId.toLong()

        override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

        override fun getCachedChannelSet(): ChannelSet = ChannelSet()

        override fun generatePacketId(): Int = nextPacketId++

        override fun sendData(p: DataPacket) = Unit

        override fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) {
            val message = initFn()
            events += message.eventName()
            if (message.get_device_metadata_request == true) {
                sessionManager.recordSession(destNum, PASSKEY)
            }
        }

        override suspend fun sendAdminAwait(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean {
            val message = initFn()
            if (message.set_channel != null || message.set_config != null) {
                writeBeforeSession = writeBeforeSession || !sessionManager.hasActiveSession(destNum)
            }
            if (message.get_device_metadata_request == true) {
                sessionManager.recordSession(destNum, PASSKEY)
            }
            message.set_channel?.let { setChannels += it }
            events += message.eventName()
            return true
        }

        override suspend fun sendAdminAwaitForSession(
            expectedRadioSessionEpoch: Long,
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean {
            val message = initFn()
            sessionAdmissionAttempts += message.eventName()
            beforeSessionAdmission?.invoke(message)
            if (currentRadioSessionEpoch() != expectedRadioSessionEpoch) return false
            return sendAdminAwait(destNum, requestId, wantResponse) { message }
        }

        override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) = Unit

        override fun requestPosition(destNum: Int, currentPosition: Position) = Unit

        override fun requestPositionOnChannel(destNum: Int, currentPosition: Position, channelIndex: Int) = Unit

        override fun setFixedPosition(destNum: Int, pos: Position) = Unit

        override fun requestUserInfo(destNum: Int) = Unit

        override fun requestTraceroute(requestId: Int, destNum: Int) = Unit

        override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) = Unit

        override fun requestNeighborInfo(requestId: Int, destNum: Int) = Unit

        private fun AdminMessage.eventName(): String = when {
            get_device_metadata_request == true -> "metadata"
            set_config != null -> "set_config"
            set_channel != null -> "set_channel"
            else -> "admin"
        }
    }

    private companion object {
        const val MY_NODE_NUM = 123
        const val RADIO_ADDRESS = "same-radio"
        val PASSKEY: ByteString = ByteString.of(1, 2, 3, 4)
        val canonicalSettings: ChannelSettings = NtsocialDefaultChannel.channelSet.settings.single()
        val defaultLora: Config.LoRaConfig = NtsocialDefaultChannel.channelSet.lora_config!!
        val configuredLocalConfig: LocalConfig =
            LocalConfig(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.US))
    }
}
