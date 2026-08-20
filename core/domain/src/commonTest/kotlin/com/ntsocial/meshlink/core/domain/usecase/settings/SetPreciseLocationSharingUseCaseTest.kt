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

import com.ntsocial.meshlink.core.model.PreciseLocationChannelSetPlanner
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.ChannelWriteOrder
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeRadioConfigRepository
import com.ntsocial.meshlink.core.testing.FakeUiPrefs
import com.ntsocial.meshlink.core.testing.TestDataFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetPreciseLocationSharingUseCaseTest {
    @Test
    fun `verified transaction enables the feed with the selected channel`() = runTest {
        val fixture = Fixture()
        fixture.uiPrefs.setPreciseLocationSharing(NODE_NUM, provide = false, channelIndex = -1)
        fixture.reliabilityManager.nextResult = ChannelReliabilityResult.VERIFIED

        val result = fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertTrue(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(TARGET_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertEquals(
            fixture.targetChannelIdentity,
            fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity,
        )
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(1, fixture.reliabilityManager.applyCount)
        assertEquals(listOf(NODE_NUM), fixture.reliabilityManager.expectedNodeNums)
        assertEquals(listOf(true), fixture.reliabilityManager.mutationLeaseWasProvided)
        assertEquals(listOf(true), fixture.reliabilityManager.requireStableSlotsValues)
        assertEquals(listOf(ChannelWriteOrder.PRECISE_POSITION_TARGET_LAST), fixture.reliabilityManager.writeOrders)
        fixture.reliabilityManager.appliedChannelSet.assertPreciseSlot(TARGET_SLOT)
    }

    @Test
    fun `verified result without a matching fresh snapshot leaves the feed disabled`() = runTest {
        val fixture = Fixture()
        fixture.uiPrefs.setPreciseLocationSharing(NODE_NUM, provide = false, channelIndex = -1)
        fixture.reliabilityManager.publishReadbackOnVerified = false

        val result = fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity)

        assertEquals(ChannelReliabilityResult.READBACK_FAILED, result)
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(-1, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(1, fixture.reliabilityManager.applyCount)
    }

    @Test
    fun `pending and radio rejection outcomes leave the feed disabled`() = runTest {
        listOf(
            ChannelReliabilityResult.VERIFICATION_PENDING,
            ChannelReliabilityResult.RADIO_REJECTED,
        ).forEach { outcome ->
            val fixture = Fixture()
            fixture.uiPrefs.setPreciseLocationSharing(
                NODE_NUM,
                provide = true,
                channelIndex = OLD_SLOT,
                channelIdentity = OLD_CHANNEL_IDENTITY,
            )
            fixture.reliabilityManager.nextResult = outcome

            val result = fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity)

            assertEquals(outcome, result)
            assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
            assertEquals(OLD_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
            assertEquals(OLD_CHANNEL_IDENTITY, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity)
            assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
            assertEquals(1, fixture.reliabilityManager.applyCount)
        }
    }

    @Test
    fun `session rotation completes enable after a fresh matching reconnect readback`() = runTest {
        val fixture = Fixture()
        fixture.reliabilityManager.nextResult = ChannelReliabilityResult.SESSION_UNAVAILABLE

        val result = async { fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity) }
        fixture.reliabilityManager.applyEntered.await()
        val currentLora = fixture.radioConfigRepository.currentChannelSet.lora_config
        fixture.radioConfigRepository.setCompleteChannelReadback(
            fixture.reliabilityManager.appliedChannelSet.copy(lora_config = currentLora),
        )

        assertEquals(ChannelReliabilityResult.VERIFIED, result.await())
        assertTrue(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(TARGET_SLOT, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIndex)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(1, fixture.reliabilityManager.applyCount)
    }

    @Test
    fun `fresh policy readback enables without rewriting the radio`() = runTest {
        val fixture = Fixture()
        val current = fixture.radioConfigRepository.currentChannelSet
        fixture.radioConfigRepository.setCompleteChannelReadback(
            PreciseLocationChannelSetPlanner.plan(current, TARGET_SLOT).copy(lora_config = current.lora_config),
        )

        val result = fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity)

        assertEquals(ChannelReliabilityResult.VERIFIED, result)
        assertTrue(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(0, fixture.reliabilityManager.applyCount)
    }

    @Test
    fun `switching slots revokes the existing feed before channel apply completes`() = runTest {
        val fixture = Fixture(blockApply = true)
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = OLD_SLOT,
            channelIdentity = OLD_CHANNEL_IDENTITY,
        )

        val result = async { fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity) }
        fixture.reliabilityManager.applyEntered.await()

        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(OLD_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertEquals(OLD_CHANNEL_IDENTITY, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        fixture.reliabilityManager.appliedChannelSet.assertPreciseSlot(TARGET_SLOT)
        fixture.reliabilityManager.releaseApply(ChannelReliabilityResult.VERIFIED)

        assertEquals(ChannelReliabilityResult.VERIFIED, result.await())
        assertTrue(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(TARGET_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertEquals(
            fixture.targetChannelIdentity,
            fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity,
        )
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
    }

    @Test
    fun `disable revokes immediately and applies zero precision to every channel`() = runTest {
        val fixture = Fixture(blockApply = true)
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = TARGET_SLOT,
            channelIdentity = fixture.targetChannelIdentity,
        )

        val result = async { fixture.useCase.disable(NODE_NUM) }
        fixture.reliabilityManager.applyEntered.await()

        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(TARGET_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertTrue(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertNull(fixture.reliabilityManager.appliedChannelSet.lora_config)
        assertEquals(
            listOf(0, 0, 0, 0, 0),
            fixture.reliabilityManager.appliedChannelSet.settings.map(ChannelSettings::positionPrecision),
        )
        assertEquals(listOf(ChannelWriteOrder.PRECISE_POSITION_TARGET_LAST), fixture.reliabilityManager.writeOrders)
        fixture.reliabilityManager.releaseApply(ChannelReliabilityResult.VERIFIED)

        assertEquals(ChannelReliabilityResult.VERIFIED, result.await())
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
    }

    @Test
    fun `disable failure keeps durable cleanup pending`() = runTest {
        val fixture = Fixture()
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = TARGET_SLOT,
            channelIdentity = fixture.targetChannelIdentity,
        )
        fixture.reliabilityManager.nextResult = ChannelReliabilityResult.SESSION_UNAVAILABLE

        val result = fixture.useCase.disable(NODE_NUM)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertTrue(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(TARGET_SLOT, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIndex)
    }

    @Test
    fun `invalid target fails closed without applying a channel set`() = runTest {
        val fixture = Fixture()
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = OLD_SLOT,
            channelIdentity = OLD_CHANNEL_IDENTITY,
        )

        val result =
            fixture.useCase.enable(
                nodeNum = NODE_NUM,
                channelIndex = 0,
                expectedChannelIdentity = fixture.targetChannelIdentity,
            )

        assertEquals(ChannelReliabilityResult.INVALID_CHANNEL_SET, result)
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(OLD_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertEquals(OLD_CHANNEL_IDENTITY, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(0, fixture.reliabilityManager.applyCount)
    }

    @Test
    fun `same slot identity replacement while queued is rejected without a radio write`() = runTest {
        val fixture = Fixture()
        val uiCapturedIdentity = fixture.targetChannelIdentity
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = OLD_SLOT,
            channelIdentity = OLD_CHANNEL_IDENTITY,
        )
        val lockEntered = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()
        val currentOwner = async {
            fixture.channelMutationLock.withLock {
                lockEntered.complete(Unit)
                releaseLock.await()
            }
        }
        lockEntered.await()

        val result = async {
            fixture.useCase.enable(
                nodeNum = NODE_NUM,
                channelIndex = TARGET_SLOT,
                expectedChannelIdentity = uiCapturedIdentity,
            )
        }
        runCurrent()
        assertEquals(2, fixture.channelMutationLock.activeOrPendingOwners.value)

        val current = fixture.radioConfigRepository.currentChannelSet
        val replacedSettings = current.settings.toMutableList()
        replacedSettings[TARGET_SLOT] = channel("NTsocial", precision = 13, keyByte = 99)
        fixture.radioConfigRepository.setChannelSet(current.copy(settings = replacedSettings))
        assertFalse(
            PreciseLocationChannelSetPlanner.channelIdentity(
                fixture.radioConfigRepository.currentChannelSet,
                TARGET_SLOT,
            ) == uiCapturedIdentity,
        )
        releaseLock.complete(Unit)
        currentOwner.await()

        assertEquals(ChannelReliabilityResult.INVALID_CHANNEL_SET, result.await())
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(OLD_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(0, fixture.reliabilityManager.applyCount)
        assertTrue(fixture.reliabilityManager.expectedNodeNums.isEmpty())
        assertTrue(fixture.reliabilityManager.mutationLeaseWasProvided.isEmpty())
        assertTrue(fixture.reliabilityManager.requireStableSlotsValues.isEmpty())
    }

    @Test
    fun `stale node is rejected before any channel apply`() = runTest {
        val fixture = Fixture(currentNodeNum = NODE_NUM + 1)
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = OLD_SLOT,
            channelIdentity = OLD_CHANNEL_IDENTITY,
        )

        val result = fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result)
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(OLD_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertEquals(OLD_CHANNEL_IDENTITY, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
        assertEquals(0, fixture.reliabilityManager.applyCount)
    }

    @Test
    fun `node replacement during apply cannot reactivate the retired feed`() = runTest {
        val fixture = Fixture(blockApply = true)
        fixture.uiPrefs.setPreciseLocationSharing(
            NODE_NUM,
            provide = true,
            channelIndex = OLD_SLOT,
            channelIdentity = OLD_CHANNEL_IDENTITY,
        )

        val result = async { fixture.useCase.enable(NODE_NUM, TARGET_SLOT, fixture.targetChannelIdentity) }
        fixture.reliabilityManager.applyEntered.await()
        fixture.nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = NODE_NUM + 1))
        fixture.reliabilityManager.releaseApply(ChannelReliabilityResult.VERIFIED)

        assertEquals(ChannelReliabilityResult.SESSION_UNAVAILABLE, result.await())
        assertFalse(fixture.uiPrefs.shouldProvideNodeLocation(NODE_NUM).value)
        assertEquals(OLD_SLOT, fixture.uiPrefs.preciseLocationChannelIndex(NODE_NUM).value)
        assertEquals(OLD_CHANNEL_IDENTITY, fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.channelIdentity)
        assertFalse(fixture.uiPrefs.preciseLocationAdmission(NODE_NUM).value.cleanupPending)
    }

    private class Fixture(blockApply: Boolean = false, currentNodeNum: Int = NODE_NUM) {
        val radioConfigRepository = FakeRadioConfigRepository().apply { setChannelSet(fiveChannelSet()) }
        val targetChannelIdentity =
            requireNotNull(
                PreciseLocationChannelSetPlanner.channelIdentity(radioConfigRepository.currentChannelSet, TARGET_SLOT),
            )
        val uiPrefs = FakeUiPrefs()
        val nodeRepository =
            FakeNodeRepository().apply { setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = currentNodeNum)) }
        val channelMutationLock = ChannelMutationLock()
        val reliabilityManager =
            RecordingChannelReliabilityManager(
                blockApply = blockApply,
                currentChannelSet = { radioConfigRepository.currentChannelSet },
                currentNodeNum = { nodeRepository.myNodeInfo.value?.myNodeNum },
                publishVerifiedReadback = radioConfigRepository::setCompleteChannelReadback,
            )
        val useCase =
            SetPreciseLocationSharingUseCase(
                radioConfigRepository = radioConfigRepository,
                channelReliabilityManager = reliabilityManager,
                uiPrefs = uiPrefs,
                nodeRepository = nodeRepository,
                channelMutationLock = channelMutationLock,
            )
    }

    private class RecordingChannelReliabilityManager(
        private val blockApply: Boolean,
        private val currentChannelSet: () -> ChannelSet,
        private val currentNodeNum: () -> Int?,
        private val publishVerifiedReadback: (ChannelSet) -> Unit,
    ) : ChannelReliabilityManager {
        override val isProtected = MutableStateFlow(false)

        var nextResult: ChannelReliabilityResult = ChannelReliabilityResult.VERIFIED
        var publishReadbackOnVerified: Boolean = true
        var applyCount: Int = 0
            private set

        lateinit var appliedChannelSet: ChannelSet
            private set

        val expectedNodeNums = mutableListOf<Int>()
        val mutationLeaseWasProvided = mutableListOf<Boolean>()
        val requireStableSlotsValues = mutableListOf<Boolean>()
        val writeOrders = mutableListOf<ChannelWriteOrder>()
        val applyEntered = CompletableDeferred<Unit>()
        private val applyResult = CompletableDeferred<ChannelReliabilityResult>()

        override suspend fun applyAndVerify(channelSet: ChannelSet): ChannelReliabilityResult = recordApply(channelSet)

        override suspend fun applyCurrentAndVerify(
            expectedNodeNum: Int,
            mutationLease: ChannelMutationLock.Lease?,
            requireStableSlots: Boolean,
            writeOrder: ChannelWriteOrder,
            transform: (ChannelSet) -> ChannelSet,
        ): ChannelReliabilityResult {
            expectedNodeNums += expectedNodeNum
            mutationLeaseWasProvided += mutationLease != null
            requireStableSlotsValues += requireStableSlots
            writeOrders += writeOrder
            if (currentNodeNum() != expectedNodeNum) return ChannelReliabilityResult.SESSION_UNAVAILABLE
            val desired =
                try {
                    transform(currentChannelSet())
                } catch (_: IllegalArgumentException) {
                    return ChannelReliabilityResult.INVALID_CHANNEL_SET
                }
            return recordApply(desired)
        }

        private suspend fun recordApply(channelSet: ChannelSet): ChannelReliabilityResult {
            applyCount += 1
            appliedChannelSet = channelSet
            applyEntered.complete(Unit)
            val result = if (blockApply) applyResult.await() else nextResult
            if (result == ChannelReliabilityResult.VERIFIED && publishReadbackOnVerified) {
                publishVerifiedReadback(channelSet)
            }
            return result
        }

        fun releaseApply(result: ChannelReliabilityResult) {
            applyResult.complete(result)
        }

        override suspend fun protectCurrentChannelSet(): ChannelReliabilityResult = ChannelReliabilityResult.PROTECTED

        override suspend fun disableProtection(): ChannelReliabilityResult =
            ChannelReliabilityResult.PROTECTION_DISABLED

        override suspend fun reconcileProtectedChannelSet(): ChannelReliabilityResult =
            ChannelReliabilityResult.NO_REPAIR_NEEDED
    }

    private companion object {
        const val NODE_NUM = 0x5D6E
        const val OLD_SLOT = 1
        const val TARGET_SLOT = 4
        const val OLD_CHANNEL_IDENTITY = "old-channel-identity"

        fun fiveChannelSet(): ChannelSet = ChannelSet(
            settings =
            listOf(
                channel("Primary", 13),
                channel("Secondary 1", 32),
                channel("Secondary 2", 19),
                channel("Secondary 3", 10),
                channel("NTsocial", 13),
            ),
            lora_config = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW),
        )

        fun channel(name: String, precision: Int, keyByte: Int = name.length): ChannelSettings = ChannelSettings(
            name = name,
            psk = ByteArray(16) { keyByte.toByte() }.toByteString(),
            module_settings = ModuleSettings(position_precision = precision),
        )
    }
}

private fun ChannelSet.assertPreciseSlot(targetSlot: Int) {
    assertNull(lora_config)
    assertEquals(listOf(0, 0, 0, 0, 32), settings.map(ChannelSettings::positionPrecision))
    assertEquals("NTsocial", settings[targetSlot].name)
}

private val ChannelSettings.positionPrecision: Int
    get() = module_settings?.position_precision ?: 0
