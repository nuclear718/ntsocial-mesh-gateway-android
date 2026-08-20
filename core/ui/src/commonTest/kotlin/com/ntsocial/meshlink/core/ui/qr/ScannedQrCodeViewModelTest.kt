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
package com.ntsocial.meshlink.core.ui.qr

import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_apply_invalid
import com.ntsocial.meshlink.core.resources.channel_apply_rejected
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.ui.component.ChannelApplyUiState
import com.ntsocial.meshlink.core.ui.util.AlertManager
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.proto.ChannelSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScannedQrCodeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val reliabilityManager: ChannelReliabilityManager = mock(MockMode.autofill)
    private lateinit var alertManager: AlertManager
    private lateinit var viewModel: ScannedQrCodeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        alertManager = AlertManager()
        viewModel =
            ScannedQrCodeViewModel(
                radioConfigRepository = radioConfigRepository,
                channelReliabilityManager = reliabilityManager,
                alertManager = alertManager,
                nodeRepository = FakeNodeRepository(),
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `readback timeout remains informational and does not show an alert`() = runTest(dispatcher) {
        everySuspend { reliabilityManager.applyAndVerify(any()) } returns
            ChannelReliabilityResult.VERIFICATION_PENDING

        viewModel.setChannels(ChannelSet())
        runCurrent()

        assertEquals(ChannelApplyUiState.WaitingForReconnect, viewModel.applyState.value)
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun `explicit NAK shows the terminal alert`() = runTest(dispatcher) {
        everySuspend { reliabilityManager.applyAndVerify(any()) } returns ChannelReliabilityResult.RADIO_REJECTED

        viewModel.setChannels(ChannelSet())
        runCurrent()

        assertIs<ChannelApplyUiState.Failed>(viewModel.applyState.value)
        assertEquals(Res.string.channel_apply_rejected, alertManager.currentAlert.value?.messageRes)
    }

    @Test
    fun `reconnect remount preserves a pending result`() = runTest(dispatcher) {
        everySuspend { reliabilityManager.applyAndVerify(any()) } returns
            ChannelReliabilityResult.VERIFICATION_PENDING

        viewModel.onDialogShown()
        viewModel.setChannels(ChannelSet())
        runCurrent()
        viewModel.onDialogShown()

        assertEquals(ChannelApplyUiState.WaitingForReconnect, viewModel.applyState.value)
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun `reconnect remount preserves verified until the dialog consumes it`() = runTest(dispatcher) {
        everySuspend { reliabilityManager.applyAndVerify(any()) } returns ChannelReliabilityResult.VERIFIED

        viewModel.onDialogShown()
        viewModel.setChannels(ChannelSet())
        runCurrent()
        viewModel.onDialogShown()

        assertEquals(ChannelApplyUiState.Verified, viewModel.applyState.value)
        viewModel.clearApplyState()
        assertEquals(ChannelApplyUiState.Idle, viewModel.applyState.value)
    }

    @Test
    fun `explicit dismissal clears a completed informational result`() = runTest(dispatcher) {
        everySuspend { reliabilityManager.applyAndVerify(any()) } returns
            ChannelReliabilityResult.VERIFICATION_PENDING

        viewModel.onDialogShown()
        viewModel.setChannels(ChannelSet())
        runCurrent()
        viewModel.onDialogDismissed()

        assertEquals(ChannelApplyUiState.Idle, viewModel.applyState.value)
    }

    @Test
    fun `invalid settings remain visible globally after immediate dismissal`() = runTest(dispatcher) {
        val applyStarted = CompletableDeferred<Unit>()
        val releaseApply = CompletableDeferred<Unit>()
        everySuspend { reliabilityManager.applyAndVerify(any()) } calls
            {
                applyStarted.complete(Unit)
                releaseApply.await()
                ChannelReliabilityResult.INVALID_CHANNEL_SET
            }

        viewModel.onDialogShown()
        val job = assertNotNull(viewModel.setChannels(ChannelSet()))
        applyStarted.await()
        viewModel.onDialogDismissed()
        releaseApply.complete(Unit)
        job.join()

        assertEquals(ChannelApplyUiState.Idle, viewModel.applyState.value)
        assertEquals(Res.string.channel_apply_invalid, alertManager.currentAlert.value?.messageRes)
    }

    @Test
    fun `a new dialog is not consumed by an earlier dismissed operation`() = runTest(dispatcher) {
        val firstApplyStarted = CompletableDeferred<Unit>()
        val releaseFirstApply = CompletableDeferred<Unit>()
        var applyCount = 0
        everySuspend { reliabilityManager.applyAndVerify(any()) } calls
            {
                applyCount++
                if (applyCount == 1) {
                    firstApplyStarted.complete(Unit)
                    releaseFirstApply.await()
                }
                ChannelReliabilityResult.VERIFIED
            }

        viewModel.onDialogShown()
        val firstJob = assertNotNull(viewModel.setChannels(ChannelSet()))
        firstApplyStarted.await()
        viewModel.onDialogDismissed()

        viewModel.onDialogShown()
        releaseFirstApply.complete(Unit)
        firstJob.join()

        assertEquals(ChannelApplyUiState.Idle, viewModel.applyState.value)
        val secondJob = assertNotNull(viewModel.setChannels(ChannelSet()))
        viewModel.onDialogDismissed()
        secondJob.join()
        assertEquals(2, applyCount)
        assertEquals(ChannelApplyUiState.Idle, viewModel.applyState.value)
    }

    @Test
    fun `setChannels claims the request before queued background work starts`() = runTest {
        val queuedDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(queuedDispatcher)
        everySuspend { reliabilityManager.applyAndVerify(any()) } returns ChannelReliabilityResult.VERIFIED
        val queuedViewModel =
            ScannedQrCodeViewModel(
                radioConfigRepository = radioConfigRepository,
                channelReliabilityManager = reliabilityManager,
                alertManager = alertManager,
                nodeRepository = FakeNodeRepository(),
            )

        queuedViewModel.onDialogShown()
        val job = assertNotNull(queuedViewModel.setChannels(ChannelSet()))

        assertEquals(ChannelApplyUiState.Applying, queuedViewModel.applyState.value)
        queuedViewModel.onDialogDismissed()
        assertEquals(ChannelApplyUiState.Applying, queuedViewModel.applyState.value)

        runCurrent()
        job.join()
        assertEquals(ChannelApplyUiState.Idle, queuedViewModel.applyState.value)
    }

    @Test
    fun `admitted transaction finishes and reports rejection after dismissal and caller cancellation`() =
        runTest(dispatcher) {
            val applyStarted = CompletableDeferred<Unit>()
            val releaseApply = CompletableDeferred<Unit>()
            var applyCount = 0
            var applyFinished = false
            everySuspend { reliabilityManager.applyAndVerify(any()) } calls
                {
                    applyCount++
                    applyStarted.complete(Unit)
                    releaseApply.await()
                    applyFinished = true
                    ChannelReliabilityResult.RADIO_REJECTED
                }

            viewModel.onDialogShown()
            val job = assertNotNull(viewModel.setChannels(ChannelSet()))
            applyStarted.await()
            assertEquals(ChannelApplyUiState.Applying, viewModel.applyState.value)

            viewModel.onDialogDismissed()
            job.cancel()
            releaseApply.complete(Unit)
            job.join()

            assertEquals(1, applyCount)
            assertTrue(applyFinished)
            assertTrue(job.isCompleted)
            assertEquals(ChannelApplyUiState.Idle, viewModel.applyState.value)
            assertEquals(Res.string.channel_apply_rejected, alertManager.currentAlert.value?.messageRes)
        }
}
