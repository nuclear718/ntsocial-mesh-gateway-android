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

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.SessionStatus
import com.ntsocial.meshlink.core.model.service.ServiceAction
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshActionHandler
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.AdminMessage
import kotlin.time.Duration.Companion.seconds

/**
 * Ensures a remote-admin session exists for the target node, dispatching a metadata request and awaiting a refreshed
 * passkey if necessary.
 *
 * Why this exists: the firmware embeds an 8-byte rotating passkey in every admin response and rejects admin traffic
 * lacking a fresh key (`firmware/src/modules/AdminModule.cpp:1460-1481`). Before this use case the UI silently tunneled
 * the user into a remote-admin screen that immediately failed if no metadata had been requested first.
 *
 * Concurrency model:
 * - One in-flight ensure per `destNum`. Concurrent callers dedupe onto the same `Deferred` so a double-tap doesn't
 *   blast two metadata requests at the radio.
 * - The refresh-flow subscription is established **before** the metadata request is dispatched to avoid losing the
 *   response on the inherently raceful `MutableSharedFlow`.
 * - The `withTimeoutOrNull` is a UX deadline only — late responses still update the durable `SessionStatus` flow that
 *   the UI observes, so a "Timeout" outcome here can self-heal in the chip without re-tapping.
 */
@Single
open class EnsureRemoteAdminSessionUseCase(
    private val sessionManager: SessionManager,
    private val meshActionHandler: MeshActionHandler,
    private val serviceRepository: ServiceRepository,
    private val commandSender: CommandSender,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val serviceScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<EnsureRequest, Deferred<EnsureSessionResult>>()

    @Suppress("ReturnCount")
    open suspend operator fun invoke(destNum: Int): EnsureSessionResult = ensure(destNum, expectedSession = null)

    /** Exact-session variant used by multi-step radio mutations. Ordinary UI callers retain the unbound overload. */
    open suspend operator fun invoke(destNum: Int, expectedRadioSessionEpoch: Long): EnsureSessionResult {
        val expectedSession =
            captureExpectedSession(expectedRadioSessionEpoch) ?: return EnsureSessionResult.Disconnected
        return ensure(destNum, expectedSession)
    }

    @Suppress("ReturnCount")
    private suspend fun ensure(destNum: Int, expectedSession: ExpectedSession?): EnsureSessionResult {
        if (
            serviceRepository.connectionState.value != ConnectionState.Connected ||
            !isExpectedSessionCurrent(expectedSession)
        ) {
            return EnsureSessionResult.Disconnected
        }
        val currentStatus = sessionManager.observeSessionStatus(destNum).first()
        if (!isExpectedSessionCurrent(expectedSession)) return EnsureSessionResult.Disconnected
        if (currentStatus is SessionStatus.Active) {
            return EnsureSessionResult.AlreadyActive
        }

        val request = EnsureRequest(destNum = destNum, radioSessionEpoch = expectedSession?.epoch)
        val deferred =
            mutex.withLock {
                inFlight[request]
                    ?: serviceScope
                        .async(start = CoroutineStart.LAZY) { runEnsure(destNum, expectedSession) }
                        .also { inFlight[request] = it }
            }
        return try {
            deferred.await().takeIf { isExpectedSessionCurrent(expectedSession) } ?: EnsureSessionResult.Disconnected
        } finally {
            mutex.withLock { if (inFlight[request] === deferred) inFlight.remove(request) }
        }
    }

    private suspend fun runEnsure(destNum: Int, expectedSession: ExpectedSession?): EnsureSessionResult {
        Logger.d { "EnsureRemoteAdminSession dispatching metadata request to $destNum" }
        return withTimeoutOrNull(UX_TIMEOUT) {
            // Subscribe BEFORE dispatching so we don't miss the refresh emission.
            val refreshed =
                serviceScope.async(start = CoroutineStart.UNDISPATCHED) {
                    sessionManager.sessionRefreshFlow.filter { it == destNum }.first()
                }
            try {
                val admitted =
                    if (expectedSession == null) {
                        meshActionHandler.onServiceAction(ServiceAction.GetDeviceMetadata(destNum))
                        true
                    } else {
                        commandSender.sendAdminAwaitForSession(
                            expectedRadioSessionEpoch = expectedSession.epoch,
                            destNum = destNum,
                            wantResponse = true,
                        ) {
                            AdminMessage(get_device_metadata_request = true)
                        }
                    }
                if (!admitted || !isExpectedSessionCurrent(expectedSession)) {
                    return@withTimeoutOrNull EnsureSessionResult.Disconnected
                }
                refreshed.await()
                if (isExpectedSessionCurrent(expectedSession)) {
                    EnsureSessionResult.Refreshed
                } else {
                    EnsureSessionResult.Disconnected
                }
            } finally {
                refreshed.cancel()
            }
        } ?: EnsureSessionResult.Timeout
    }

    private fun captureExpectedSession(expectedRadioSessionEpoch: Long): ExpectedSession? =
        radioInterfaceService.radioSessionState.value.let { session ->
            session
                .takeIf { it.epoch == expectedRadioSessionEpoch && it.isConfiguredReady }
                ?.selectedDeviceAddress
                ?.let { address -> ExpectedSession(epoch = expectedRadioSessionEpoch, address = address) }
        }

    private fun isExpectedSessionCurrent(expectedSession: ExpectedSession?): Boolean = expectedSession == null ||
        radioInterfaceService.radioSessionState.value.let { session ->
            session.epoch == expectedSession.epoch &&
                session.selectedDeviceAddress == expectedSession.address &&
                session.activeDeviceAddress == expectedSession.address &&
                session.isConfiguredReady
        }

    private data class EnsureRequest(val destNum: Int, val radioSessionEpoch: Long?)

    private data class ExpectedSession(val epoch: Long, val address: String)

    companion object {
        /**
         * UX deadline for surfacing a result to the user. The metadata request keeps flying after this — late responses
         * still update the durable `SessionStatus` flow.
         */
        val UX_TIMEOUT = 10.seconds
    }
}
