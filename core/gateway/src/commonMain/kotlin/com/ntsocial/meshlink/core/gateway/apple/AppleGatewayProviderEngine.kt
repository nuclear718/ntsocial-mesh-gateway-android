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
package com.ntsocial.meshlink.core.gateway.apple

import kotlinx.coroutines.CancellationException
import okio.ByteString
import okio.ByteString.Companion.toByteString

data class AppleGatewayCallerCredentials(
    val callerId: String,
    val activeKeyVersion: Int,
    val authenticationKey: ByteString,
) {
    init {
        require(callerId.isNotBlank())
        require(activeKeyVersion > 0)
        require(authenticationKey.size == AppleGatewayContract.AUTHENTICATION_KEY_SIZE_BYTES)
    }
}

sealed interface AppleGatewayProcessOutcome {
    data object NoCommand : AppleGatewayProcessOutcome

    data class Accepted(val result: AppleGatewayCommandResult, val replayed: Boolean) : AppleGatewayProcessOutcome

    data class Rejected(val result: AppleGatewayCommandResult, val retryable: Boolean) : AppleGatewayProcessOutcome
}

/**
 * Pure KMP provider processor for the Apple App Group mailbox.
 *
 * The shared database is only a mailbox/projection. Route resolution is always performed against [routeRegistry], and
 * restart-stable idempotency is always performed against [ledger], which must point at MeshLink-private storage.
 */
@Suppress("TooManyFunctions")
class AppleGatewayProviderEngine(
    private val store: AppleGatewayStore,
    private val ledger: AppleGatewayLedger,
    private val radioPort: AppleGatewayRadioPort,
    private val routeRegistry: AppleGatewayRouteRegistry,
    private val credentials: AppleGatewayCallerCredentials,
    val providerInstanceId: String = newProviderInstanceId(),
    private val wakeSink: AppleGatewayWakeSink = NoopAppleGatewayWakeSink,
) {
    init {
        require(providerInstanceId.isNotBlank())
    }

    /** Atomically replaces status, caller and channel projections from one radio snapshot. */
    suspend fun refreshProjection(nowMillis: Long): AppleGatewayStatus {
        val snapshot = radioPort.snapshot()
        return publishProjection(snapshot, nowMillis)
    }

    /**
     * Renews a usable projection without periodically publishing routes for a non-ready radio.
     *
     * The readiness check and published channel data come from the same immutable radio snapshot. A caller must still
     * serialize this with command processing; route generation and source/slot validation remain owned by
     * [routeRegistry].
     */
    suspend fun refreshProjectionIfReady(nowMillis: Long): AppleGatewayStatus? {
        val snapshot = radioPort.snapshot()
        if (snapshot.readiness != AppleGatewayReadiness.READY) return null
        return publishProjection(snapshot, nowMillis)
    }

    private suspend fun publishProjection(snapshot: AppleGatewayRadioSnapshot, nowMillis: Long): AppleGatewayStatus {
        val issuedRoutes =
            routeRegistry.issueRoutes(
                callerId = credentials.callerId,
                channels = snapshot.channels,
                nowMillis = nowMillis,
                newRoutingContext = snapshot.routingContext,
            )
        val routeBySlot = issuedRoutes.routes.associateBy(AppleGatewayRoute::capturedSlotIndex)
        val status =
            AppleGatewayStatus(
                providerInstanceId = providerInstanceId,
                readiness = snapshot.readiness,
                radioGeneration = issuedRoutes.radioGeneration,
                historyEpoch = snapshot.historyEpoch,
                overlayHighWater = snapshot.overlayHighWater,
                nativeTextHighWater = snapshot.nativeTextHighWater,
                activeKeyVersion = credentials.activeKeyVersion,
                updatedAtMillis = nowMillis,
            )
        val channels =
            snapshot.channels.map { channel ->
                val route = checkNotNull(routeBySlot[channel.slotIndex])
                AppleGatewayChannelProjection(
                    sourceChannelId = channel.sourceChannelId,
                    slotIndex = channel.slotIndex,
                    displayName = channel.displayName,
                    role = channel.role,
                    securityClass = channel.securityClass,
                    capabilities = channel.capabilities,
                    routeToken = route.token,
                    routeExpiresAtMillis = route.expiresAtMillis,
                    radioGeneration = issuedRoutes.radioGeneration,
                )
            }
        store.replaceProjection(
            status = status,
            channels = channels,
            caller =
            AppleGatewayCallerProjection(
                callerId = credentials.callerId,
                activeKeyVersion = credentials.activeKeyVersion,
                revoked = false,
                lastSeenAtMillis = nowMillis,
            ),
        )
        wakeSink.stateChanged()
        return status
    }

    /** Refreshes authoritative routes, then claims and processes at most one durable command. */
    suspend fun processNext(nowMillis: Long): AppleGatewayProcessOutcome {
        refreshProjection(nowMillis)
        val command =
            store.claimNextCommand(providerInstanceId = providerInstanceId, claimedAtMillis = nowMillis)
                ?: return AppleGatewayProcessOutcome.NoCommand
        return processClaimed(command, nowMillis)
    }

    suspend fun appendOverlayIngress(ingress: AppleGatewayOverlayIngress): Boolean =
        store.appendOverlayIngress(ingress).also { inserted -> if (inserted) wakeSink.stateChanged() }

    suspend fun appendNextOverlayIngress(
        historyEpoch: String,
        payload: AppleGatewayOverlayIngressPayload,
    ): AppleGatewayOverlayIngress =
        store.appendNextOverlayIngress(historyEpoch, payload).also { wakeSink.stateChanged() }

    suspend fun appendNativeMessageChange(change: AppleGatewayNativeMessageChange): Boolean =
        store.appendNativeMessageChange(change).also { inserted -> if (inserted) wakeSink.stateChanged() }

    suspend fun resetCaller() {
        routeRegistry.revokeCaller(credentials.callerId)
        store.resetCallerProjection(credentials.callerId)
        wakeSink.stateChanged()
    }

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun processClaimed(command: AppleGatewayCommand, nowMillis: Long): AppleGatewayProcessOutcome {
        val preAuthenticationFailure =
            AppleGatewayCommandValidator.preAuthenticationFailure(
                command = command,
                expectedCallerId = credentials.callerId,
                activeKeyVersion = credentials.activeKeyVersion,
                nowMillis = nowMillis,
            )
        preAuthenticationFailure
            ?.takeUnless { reason -> reason == AppleGatewayRejectionReason.EXPIRED }
            ?.let { reason ->
                return reject(command, reason, nowMillis)
            }

        if (!AppleGatewayAuthenticator.verify(command, credentials.authenticationKey)) {
            return reject(command, AppleGatewayRejectionReason.INVALID_AUTHENTICATION, nowMillis)
        }

        val canonicalClientMessageId = command.clientMessageId.uppercase()
        val requestFingerprint = AppleGatewayCommandCodec.requestFingerprint(command)

        ledger.lookup(credentials.callerId, canonicalClientMessageId)?.let { existing ->
            when (
                val replay =
                    AppleGatewayIdempotencyPolicy.reserve(
                        callerId = credentials.callerId,
                        canonicalClientMessageId = canonicalClientMessageId,
                        requestFingerprint = requestFingerprint,
                        existing = existing,
                    )
            ) {
                AppleGatewayLedgerReservation.Conflict ->
                    return reject(command, AppleGatewayRejectionReason.IDEMPOTENCY_CONFLICT, nowMillis)

                is AppleGatewayLedgerReservation.Accepted ->
                    return acceptResult(command, replay.packetId, nowMillis, replayed = true)

                is AppleGatewayLedgerReservation.Pending -> Unit
            }
        }

        // Expiry prevents new admission, but not reconstruction of an already durable ACCEPTED result after a crash.
        if (preAuthenticationFailure == AppleGatewayRejectionReason.EXPIRED) {
            return reject(command, AppleGatewayRejectionReason.EXPIRED, nowMillis)
        }

        val route =
            routeRegistry.resolve(command, nowMillis)
                ?: return reject(command, AppleGatewayRejectionReason.INVALID_ROUTE, nowMillis)

        if (
            store.reserveNonceForProcessing(command, requestFingerprint, nowMillis) ==
            AppleGatewayNonceReservation.REPLAY
        ) {
            return reject(command, AppleGatewayRejectionReason.NONCE_REPLAY, nowMillis)
        }

        AppleGatewayCommandValidator.bodyFailure(command.body)?.let { reason ->
            return reject(command, reason, nowMillis)
        }

        return when (
            val reservation = ledger.reserve(credentials.callerId, canonicalClientMessageId, requestFingerprint)
        ) {
            AppleGatewayLedgerReservation.Conflict ->
                reject(command, AppleGatewayRejectionReason.IDEMPOTENCY_CONFLICT, nowMillis)

            is AppleGatewayLedgerReservation.Accepted ->
                acceptResult(command, reservation.packetId, nowMillis, replayed = true)

            is AppleGatewayLedgerReservation.Pending ->
                admitPending(
                    command = command,
                    route = route,
                    canonicalClientMessageId = canonicalClientMessageId,
                    requestFingerprint = requestFingerprint,
                    packetId = reservation.packetId,
                    nowMillis = nowMillis,
                )
        }
    }

    @Suppress("LongMethod")
    private suspend fun admitPending(
        command: AppleGatewayCommand,
        route: AppleGatewayRoute,
        canonicalClientMessageId: String,
        requestFingerprint: String,
        packetId: Int,
        nowMillis: Long,
    ): AppleGatewayProcessOutcome {
        val admission =
            try {
                when (val body = command.body) {
                    is AppleGatewayCommandBody.NtsocialEnvelope ->
                        radioPort.durablyAdmitOverlay(
                            AppleGatewayOverlayAdmission(
                                packetId = packetId,
                                canonicalClientMessageId = canonicalClientMessageId,
                                sourceChannelId = route.sourceChannelId,
                                capturedSlotIndex = route.capturedSlotIndex,
                                rawEnvelope = body.rawEnvelope,
                                destination = body.destination,
                                hopLimit = body.hopLimit,
                                wantAck = body.wantAck,
                            ),
                        )

                    is AppleGatewayCommandBody.NativeBroadcastText ->
                        radioPort.durablyAdmitNativeText(
                            AppleGatewayNativeTextAdmission(
                                packetId = packetId,
                                canonicalClientMessageId = canonicalClientMessageId,
                                sourceChannelId = route.sourceChannelId,
                                capturedSlotIndex = route.capturedSlotIndex,
                                text = body.text,
                            ),
                        )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                AppleGatewayRadioAdmissionResult.TransientFailure(AppleGatewayRejectionReason.QUEUE_FAILED)
            }

        return when (admission) {
            AppleGatewayRadioAdmissionResult.Accepted -> {
                val ledgerAccepted =
                    try {
                        ledger.markAccepted(
                            callerId = credentials.callerId,
                            canonicalClientMessageId = canonicalClientMessageId,
                            requestFingerprint = requestFingerprint,
                            packetId = packetId,
                        )
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        false
                    }
                if (ledgerAccepted) {
                    acceptResult(command, packetId, nowMillis, replayed = false)
                } else {
                    // Admission may already be scheduled. Keep PENDING and report a retryable local-commit failure.
                    rejectTransient(command, AppleGatewayRejectionReason.QUEUE_FAILED, nowMillis, packetId)
                }
            }

            is AppleGatewayRadioAdmissionResult.PermanentFailure ->
                reject(command, admission.reason, nowMillis, packetId)

            is AppleGatewayRadioAdmissionResult.TransientFailure ->
                rejectTransient(command, admission.reason, nowMillis, packetId)
        }
    }

    private suspend fun acceptResult(
        command: AppleGatewayCommand,
        packetId: Int,
        nowMillis: Long,
        replayed: Boolean,
    ): AppleGatewayProcessOutcome.Accepted {
        val result =
            store.appendNextResult(
                callerId = command.callerId,
                clientMessageId = command.clientMessageId,
                state = AppleGatewayCommandResultState.ACCEPTED_LOCAL,
                packetId = packetId,
                reason = null,
                updatedAtMillis = nowMillis,
            )
        wakeSink.stateChanged()
        return AppleGatewayProcessOutcome.Accepted(result, replayed)
    }

    private suspend fun reject(
        command: AppleGatewayCommand,
        reason: AppleGatewayRejectionReason,
        nowMillis: Long,
        packetId: Int? = null,
    ): AppleGatewayProcessOutcome.Rejected {
        val result =
            store.appendNextResult(
                callerId = command.callerId,
                clientMessageId = command.clientMessageId,
                state = AppleGatewayCommandResultState.REJECTED,
                packetId = packetId,
                reason = reason,
                updatedAtMillis = nowMillis,
            )
        wakeSink.stateChanged()
        return AppleGatewayProcessOutcome.Rejected(result, retryable = false)
    }

    private suspend fun rejectTransient(
        command: AppleGatewayCommand,
        reason: AppleGatewayRejectionReason,
        nowMillis: Long,
        packetId: Int,
    ): AppleGatewayProcessOutcome.Rejected {
        val result =
            store.appendNextResult(
                callerId = command.callerId,
                clientMessageId = command.clientMessageId,
                state = AppleGatewayCommandResultState.PENDING_PROVIDER_WAKE,
                packetId = packetId,
                reason = reason,
                updatedAtMillis = nowMillis,
            )
        store.releaseClaim(command.callerId, command.clientMessageId, providerInstanceId)
        wakeSink.stateChanged()
        return AppleGatewayProcessOutcome.Rejected(result, retryable = true)
    }
}

private fun newProviderInstanceId(): String =
    PlatformAppleGatewayRandomSource.nextBytes(AppleGatewayContract.ROUTE_TOKEN_SIZE_BYTES)
        .toByteString()
        .base64Url()
        .trimEnd('=')
