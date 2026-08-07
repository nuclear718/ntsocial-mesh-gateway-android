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
package com.ntsocial.meshlink.core.domain.usecase.session

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.SessionStatus
import com.ntsocial.meshlink.core.model.service.ServiceAction
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshActionHandler
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class EnsureRemoteAdminSessionUseCaseTest {

    private val destNum = 0xCAFE

    private fun stubSessionManager(
        initialStatus: SessionStatus = SessionStatus.NoSession,
        refreshFlow: MutableSharedFlow<Int> = MutableSharedFlow(extraBufferCapacity = 8),
    ): SessionManager {
        val mgr = mock<SessionManager>(MockMode.autofill)
        every { mgr.observeSessionStatus(any()) } returns flowOf(initialStatus)
        every { mgr.sessionRefreshFlow } returns refreshFlow
        every { mgr.getPasskey(any()) } returns ByteString.EMPTY
        return mgr
    }

    private fun connectedRepo(state: ConnectionState = ConnectionState.Connected): ServiceRepository {
        val repo = mock<ServiceRepository>(MockMode.autofill)
        every { repo.connectionState } returns MutableStateFlow(state)
        return repo
    }

    @Test
    fun `returns Disconnected without dispatching when not connected`() = runTest {
        val sessionManager = stubSessionManager()
        val handler = mock<MeshActionHandler>(MockMode.autofill)
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                handler,
                connectedRepo(ConnectionState.Disconnected),
                mock<CommandSender>(MockMode.autofill),
                mock<RadioInterfaceService>(MockMode.autofill),
                this,
            )

        val result = useCase(destNum)

        assertEquals(EnsureSessionResult.Disconnected, result)
    }

    @Test
    fun `returns AlreadyActive without dispatching when status already Active`() = runTest {
        val active = SessionStatus.Active(Clock.System.now())
        val sessionManager = stubSessionManager(initialStatus = active)
        val handler = mock<MeshActionHandler>(MockMode.autofill)
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                handler,
                connectedRepo(),
                mock<CommandSender>(MockMode.autofill),
                mock<RadioInterfaceService>(MockMode.autofill),
                this,
            )

        val result = useCase(destNum)

        assertEquals(EnsureSessionResult.AlreadyActive, result)
    }

    @Test
    fun `dispatches metadata request and returns Refreshed when refresh flow emits`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val handler = mock<MeshActionHandler>(MockMode.autofill)
        // Simulate the radio responding by emitting on the refresh flow when the metadata request fires.
        everySuspend { handler.onServiceAction(any()) } calls
            {
                refresh.tryEmit(destNum)
                Unit
            }

        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                handler,
                connectedRepo(),
                mock<CommandSender>(MockMode.autofill),
                mock<RadioInterfaceService>(MockMode.autofill),
                this,
            )

        val result = useCase(destNum)

        assertEquals(EnsureSessionResult.Refreshed, result)
        verifySuspend { handler.onServiceAction(ServiceAction.GetDeviceMetadata(destNum)) }
    }

    @Test
    fun `returns Timeout when no refresh arrives within deadline`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val handler = mock<MeshActionHandler>(MockMode.autofill)
        everySuspend { handler.onServiceAction(any()) } returns Unit

        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                handler,
                connectedRepo(),
                mock<CommandSender>(MockMode.autofill),
                mock<RadioInterfaceService>(MockMode.autofill),
                this,
            )

        var observed: EnsureSessionResult? = null
        val job = launch { observed = useCase(destNum) }
        advanceTimeBy(EnsureRemoteAdminSessionUseCase.UX_TIMEOUT.inWholeMilliseconds + 100)
        advanceUntilIdle()
        job.join()

        assertEquals(EnsureSessionResult.Timeout, observed)
    }

    @Test
    fun `retired exact session cannot dispatch metadata into same-address replacement`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val handler = mock<MeshActionHandler>(MockMode.autofill)
        val commandSender = mock<CommandSender>(MockMode.autofill)
        val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
        val sessionState = MutableStateFlow(readySession(epoch = 10))
        every { radioInterfaceService.radioSessionState } returns sessionState
        everySuspend { commandSender.sendAdminAwaitForSession(any(), any(), any(), any(), any()) } calls
            {
                sessionState.value = readySession(epoch = 11)
                refresh.tryEmit(destNum)
                false
            }
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                handler,
                connectedRepo(),
                commandSender,
                radioInterfaceService,
                this,
            )

        val result = useCase(destNum, expectedRadioSessionEpoch = 10)

        assertEquals(EnsureSessionResult.Disconnected, result)
        verifySuspend(mode = VerifyMode.not) { handler.onServiceAction(any()) }
    }

    @Test
    fun `replacement session passkey cannot satisfy retired exact request`() = runTest {
        val sessionState = MutableStateFlow(readySession(epoch = 20))
        val sessionManager = stubSessionManager()
        every { sessionManager.observeSessionStatus(any()) } returns
            flow {
                sessionState.value = readySession(epoch = 21)
                emit(SessionStatus.Active(Clock.System.now()))
            }
        val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
        every { radioInterfaceService.radioSessionState } returns sessionState
        val commandSender = mock<CommandSender>(MockMode.autofill)
        val handler = mock<MeshActionHandler>(MockMode.autofill)
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                handler,
                connectedRepo(),
                commandSender,
                radioInterfaceService,
                this,
            )

        val result = useCase(destNum, expectedRadioSessionEpoch = 20)

        assertEquals(EnsureSessionResult.Disconnected, result)
        verifySuspend(mode = VerifyMode.not) {
            commandSender.sendAdminAwaitForSession(any(), any(), any(), any(), any())
        }
    }

    private fun readySession(epoch: Long): RadioSessionState = RadioSessionState(
        epoch = epoch,
        selectedDeviceAddress = "same-radio",
        activeDeviceAddress = "same-radio",
        transportConnectionState = ConnectionState.Connected,
        configured = true,
    )
}
