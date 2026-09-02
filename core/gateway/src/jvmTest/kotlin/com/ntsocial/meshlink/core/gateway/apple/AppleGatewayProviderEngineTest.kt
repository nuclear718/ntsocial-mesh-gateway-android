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

import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleGatewayProviderEngineTest {
    private val directory = Files.createTempDirectory("meshlink-provider-engine-")
    private val store = AppleGatewayStore(directory.resolve("shared.sqlite").toString())

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `invalid hmac wins before invalid route and never reaches nonce ledger or radio`() = runTest {
        val events = mutableListOf<String>()
        val ledger = RecordingLedger(events)
        val radio = FakeRadioPort(events)
        val engine = engine(ledger, radio)
        engine.refreshProjection(NOW)
        val projected = store.readChannels().single()
        val invalid =
            signedCommand(projected)
                .copy(
                    routeToken = differentRouteToken(),
                    authenticationTag = ByteString.of(*ByteArray(AppleGatewayContract.AUTHENTICATION_TAG_SIZE_BYTES)),
                )
        store.enqueueCommand(invalid, NOW)

        val outcome = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 1))

        assertEquals(AppleGatewayRejectionReason.INVALID_AUTHENTICATION, outcome.result.reason)
        assertEquals(emptyList(), events)
        assertEquals(0, radio.admissions.size)
    }

    @Test
    fun `route is invalidated when current channel snapshot changes generation`() = runTest {
        val radio = FakeRadioPort()
        val engine = engine(privateLedger(), radio)
        engine.refreshProjection(NOW)
        val command = signedCommand(store.readChannels().single())
        store.enqueueCommand(command, NOW)
        val priorGeneration = command.radioGeneration
        radio.snapshotValue = radio.snapshotValue.copy(channels = listOf(channel().copy(displayName = "changed")))

        val outcome = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 1))

        assertEquals(AppleGatewayRejectionReason.INVALID_ROUTE, outcome.result.reason)
        assertNotEquals(priorGeneration, store.readStatus()?.radioGeneration)
        assertEquals(0, radio.admissions.size)
    }

    @Test
    fun `ready liveness refresh keeps the projected route fresh beyond its original ttl`() = runTest {
        val radio = FakeRadioPort()
        val registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource())
        val engine = engine(privateLedger(), radio, registry)
        engine.refreshProjection(NOW)
        val original = store.readChannels().single()

        engine.refreshProjectionIfReady(NOW + AppleGatewayContract.ROUTE_TTL_MILLIS / 2)
        engine.refreshProjectionIfReady(NOW + AppleGatewayContract.ROUTE_TTL_MILLIS + 1)
        val renewed = store.readChannels().single()

        assertEquals(original.radioGeneration, renewed.radioGeneration)
        assertNotEquals(original.routeToken, renewed.routeToken)
        assertEquals(NOW + AppleGatewayContract.ROUTE_TTL_MILLIS * 2 + 1, renewed.routeExpiresAtMillis)
        assertTrue(renewed.routeExpiresAtMillis > NOW + AppleGatewayContract.ROUTE_TTL_MILLIS)
    }

    @Test
    fun `liveness refresh does not publish or wake while radio is not ready`() = runTest {
        val radio = FakeRadioPort()
        var wakeCount = 0
        val engine = engine(ledger = privateLedger(), radio = radio, wakeSink = AppleGatewayWakeSink { wakeCount += 1 })
        engine.refreshProjection(NOW)
        val originalStatus = store.readStatus()
        val originalChannels = store.readChannels()
        assertEquals(1, wakeCount)
        radio.snapshotValue =
            radio.snapshotValue.copy(readiness = AppleGatewayReadiness.DISCONNECTED, channels = emptyList())

        assertNull(engine.refreshProjectionIfReady(NOW + AppleGatewayContract.ROUTE_TTL_MILLIS / 2))

        assertEquals(originalStatus, store.readStatus())
        assertEquals(originalChannels, store.readChannels())
        assertEquals(1, wakeCount)
    }

    @Test
    fun `liveness refresh rotates private context and cannot revive the stale route`() = runTest {
        val radio = FakeRadioPort()
        val engine = engine(privateLedger(), radio)
        engine.refreshProjection(NOW)
        val staleCommand = signedCommand(store.readChannels().single())
        radio.snapshotValue = radio.snapshotValue.copy(routingContext = "changed-context".encodeUtf8())

        engine.refreshProjectionIfReady(NOW + 1)
        val current = store.readChannels().single()
        assertNotEquals(staleCommand.radioGeneration, current.radioGeneration)
        store.enqueueCommand(staleCommand, NOW + 1)

        val outcome = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 2))

        assertEquals(AppleGatewayRejectionReason.INVALID_ROUTE, outcome.result.reason)
        assertEquals(0, radio.admissions.size)
    }

    @Test
    fun `nonce replay for a different command is rejected before ledger admission`() = runTest {
        val radio = FakeRadioPort()
        val engine = engine(privateLedger(), radio)
        engine.refreshProjection(NOW)
        val projection = store.readChannels().single()
        val nonce = nonce(7)
        val first = signedCommand(projection, clientMessageId = CLIENT_ID, nonce = nonce, body = overlay("one"))
        store.enqueueCommand(first, NOW)
        assertIs<AppleGatewayProcessOutcome.Accepted>(engine.processNext(NOW + 1))

        val second =
            signedCommand(
                store.readChannels().single(),
                clientMessageId = OTHER_CLIENT_ID,
                nonce = nonce,
                body = overlay("two"),
            )
        store.enqueueCommand(second, NOW + 2)
        val replay = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 3))

        assertEquals(AppleGatewayRejectionReason.NONCE_REPLAY, replay.result.reason)
        assertEquals(1, radio.admissions.size)
    }

    @Test
    fun `accepted replay does not readmit while different fingerprint conflicts`() = runTest {
        val radio = FakeRadioPort()
        val engine = engine(privateLedger(), radio)
        engine.refreshProjection(NOW)
        val first = signedCommand(store.readChannels().single(), body = overlay("same"), nonce = nonce(1))
        store.enqueueCommand(first, NOW)
        val accepted = assertIs<AppleGatewayProcessOutcome.Accepted>(engine.processNext(NOW + 1))
        assertEquals(false, accepted.replayed)

        engine.resetCaller()
        engine.refreshProjection(NOW + 2)
        val replay = signedCommand(store.readChannels().single(), body = overlay("same"), nonce = nonce(2))
        store.enqueueCommand(replay, NOW + 2)
        val replayed = assertIs<AppleGatewayProcessOutcome.Accepted>(engine.processNext(NOW + 3))
        assertTrue(replayed.replayed)
        assertEquals(accepted.result.packetId, replayed.result.packetId)
        assertEquals(1, radio.admissions.size)

        engine.resetCaller()
        engine.refreshProjection(NOW + 4)
        val conflict = signedCommand(store.readChannels().single(), body = overlay("different"), nonce = nonce(3))
        store.enqueueCommand(conflict, NOW + 4)
        val rejected = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 5))
        assertEquals(AppleGatewayRejectionReason.IDEMPOTENCY_CONFLICT, rejected.result.reason)
        assertEquals(1, radio.admissions.size)
    }

    @Test
    fun `commit order is pending then durable native admission then accepted then accepted local result`() = runTest {
        val events = mutableListOf<String>()
        val ledger = RecordingLedger(events)
        val radio = FakeRadioPort(events)
        val engine = engine(ledger, radio)
        engine.refreshProjection(NOW)
        val command =
            signedCommand(
                store.readChannels().single(),
                body = AppleGatewayCommandBody.NativeBroadcastText("hello mesh"),
            )
        store.enqueueCommand(command, NOW)

        val outcome = assertIs<AppleGatewayProcessOutcome.Accepted>(engine.processNext(NOW + 1))

        assertEquals(listOf("ledger:PENDING", "radio:NATIVE", "ledger:ACCEPTED"), events)
        assertEquals(AppleGatewayCommandResultState.ACCEPTED_LOCAL, outcome.result.state)
        val admission = assertIs<AppleGatewayNativeTextAdmission>(radio.admissions.single())
        assertEquals(3, admission.capturedSlotIndex)
        assertEquals(command.clientMessageId, admission.canonicalClientMessageId)
    }

    @Test
    fun `ledger acceptance failure after durable admission is retryable and releases claim`() = runTest {
        val events = mutableListOf<String>()
        val ledger = RecordingLedger(events, markAcceptedFailuresRemaining = 1)
        val radio = FakeRadioPort(events)
        val engine = engine(ledger, radio)
        engine.refreshProjection(NOW)
        val command = signedCommand(store.readChannels().single())
        store.enqueueCommand(command, NOW)

        val failedCommit = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 1))

        assertTrue(failedCommit.retryable)
        assertEquals(AppleGatewayCommandResultState.PENDING_PROVIDER_WAKE, failedCommit.result.state)
        assertEquals(AppleGatewayRejectionReason.QUEUE_FAILED, failedCommit.result.reason)
        assertEquals(listOf("ledger:PENDING", "radio:OVERLAY", "ledger:ACCEPTED_FAILED"), events)

        val retried = assertIs<AppleGatewayProcessOutcome.Accepted>(engine.processNext(NOW + 2))

        assertEquals(false, retried.replayed)
        assertEquals(2, radio.admissions.size)
        assertEquals(
            listOf("ledger:PENDING", "radio:OVERLAY", "ledger:ACCEPTED_FAILED", "radio:OVERLAY", "ledger:ACCEPTED"),
            events,
        )
    }

    @Test
    fun `accepted ledger crash point replays before new process route resolution and conflicts on changed content`() =
        runTest {
            val radio = FakeRadioPort()
            val ledger = privateLedger()
            val beforeCrash =
                engine(
                    ledger = ledger,
                    radio = radio,
                    registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource()),
                    providerId = "provider-before-result-crash",
                )
            beforeCrash.refreshProjection(NOW)
            val command = signedCommand(store.readChannels().single(), body = overlay("accepted-before-crash"))
            store.enqueueCommand(command, NOW)
            assertEquals(command, store.claimNextCommand("provider-before-result-crash", NOW + 1))
            val fingerprint = AppleGatewayCommandCodec.requestFingerprint(command)
            val reservation =
                assertIs<AppleGatewayLedgerReservation.Pending>(ledger.reserve(CALLER, CLIENT_ID, fingerprint))
            ledger.markAccepted(CALLER, CLIENT_ID, fingerprint, reservation.packetId)

            val restarted =
                engine(
                    ledger = AppleGatewayPrivateLedger(directory.resolve("private.sqlite").toString()),
                    radio = radio,
                    registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource(initialCounter = 100)),
                    providerId = "provider-after-result-crash",
                )
            val afterCommandExpiry = NOW + AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS + 1
            val replayed = assertIs<AppleGatewayProcessOutcome.Accepted>(restarted.processNext(afterCommandExpiry))

            assertTrue(replayed.replayed)
            assertEquals(reservation.packetId, replayed.result.packetId)
            assertEquals(AppleGatewayCommandResultState.ACCEPTED_LOCAL, replayed.result.state)
            assertEquals(0, radio.admissions.size)

            restarted.resetCaller()
            restarted.refreshProjection(afterCommandExpiry + 1)
            val conflict =
                signedCommand(store.readChannels().single(), body = overlay("different-after-crash"), nonce = nonce(9))
            store.enqueueCommand(conflict, NOW + 3)
            radio.snapshotValue = radio.snapshotValue.copy(channels = listOf(channel().copy(displayName = "changed")))

            val rejected = assertIs<AppleGatewayProcessOutcome.Rejected>(restarted.processNext(afterCommandExpiry + 2))

            assertEquals(AppleGatewayRejectionReason.IDEMPOTENCY_CONFLICT, rejected.result.reason)
            assertEquals(0, radio.admissions.size)
        }

    @Test
    fun `transient failure releases claim while permanent failure is terminal`() = runTest {
        val radio = FakeRadioPort()
        radio.nextAdmission =
            AppleGatewayRadioAdmissionResult.TransientFailure(AppleGatewayRejectionReason.RADIO_NOT_READY)
        val engine = engine(privateLedger(), radio)
        engine.refreshProjection(NOW)
        val command = signedCommand(store.readChannels().single())
        store.enqueueCommand(command, NOW)

        val transient = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 1))
        assertTrue(transient.retryable)
        assertEquals(AppleGatewayCommandResultState.PENDING_PROVIDER_WAKE, transient.result.state)
        assertEquals(AppleGatewayRejectionReason.RADIO_NOT_READY, transient.result.reason)

        radio.nextAdmission = AppleGatewayRadioAdmissionResult.Accepted
        assertIs<AppleGatewayProcessOutcome.Accepted>(engine.processNext(NOW + 2))
        assertEquals(2, radio.admissions.size)
        assertIs<AppleGatewayProcessOutcome.NoCommand>(engine.processNext(NOW + 3))

        engine.resetCaller()
        engine.refreshProjection(NOW + 4)
        val permanentCommand =
            signedCommand(store.readChannels().single(), clientMessageId = OTHER_CLIENT_ID, nonce = nonce(8))
        store.enqueueCommand(permanentCommand, NOW + 4)
        radio.nextAdmission =
            AppleGatewayRadioAdmissionResult.PermanentFailure(AppleGatewayRejectionReason.RADIO_REJECTED)
        val permanent = assertIs<AppleGatewayProcessOutcome.Rejected>(engine.processNext(NOW + 5))
        assertEquals(false, permanent.retryable)
        assertEquals(AppleGatewayCommandResultState.REJECTED, permanent.result.state)
        assertEquals(AppleGatewayRejectionReason.RADIO_REJECTED, permanent.result.reason)
        assertIs<AppleGatewayProcessOutcome.NoCommand>(engine.processNext(NOW + 6))
    }

    @Test
    fun `new process reclaims old claim but rejects route projected by previous process`() = runTest {
        val radio = FakeRadioPort()
        val firstEngine =
            engine(
                ledger = privateLedger(),
                radio = radio,
                registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource()),
                providerId = "provider-before-crash",
            )
        firstEngine.refreshProjection(NOW)
        val command = signedCommand(store.readChannels().single())
        store.enqueueCommand(command, NOW)
        assertEquals(command, store.claimNextCommand("provider-before-crash", NOW + 1))

        val restarted =
            engine(
                ledger = privateLedger(),
                radio = radio,
                registry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource(initialCounter = 100)),
                providerId = "provider-after-crash",
            )
        val outcome = assertIs<AppleGatewayProcessOutcome.Rejected>(restarted.processNext(NOW + 2))

        assertEquals(AppleGatewayRejectionReason.INVALID_ROUTE, outcome.result.reason)
        assertEquals(0, radio.admissions.size)
    }

    private fun engine(
        ledger: AppleGatewayLedger,
        radio: FakeRadioPort,
        registry: AppleGatewayRouteRegistry = AppleGatewayRouteRegistry(DeterministicAppleGatewayRandomSource()),
        providerId: String = "provider-instance",
        wakeSink: AppleGatewayWakeSink = NoopAppleGatewayWakeSink,
    ) = AppleGatewayProviderEngine(
        store = store,
        ledger = ledger,
        radioPort = radio,
        routeRegistry = registry,
        credentials = AppleGatewayCallerCredentials(CALLER, 1, KEY),
        providerInstanceId = providerId,
        wakeSink = wakeSink,
    )

    private fun privateLedger() = AppleGatewayPrivateLedger(directory.resolve("private.sqlite").toString())

    private suspend fun signedCommand(
        projection: AppleGatewayChannelProjection,
        clientMessageId: String = CLIENT_ID,
        nonce: ByteString = nonce(0),
        body: AppleGatewayCommandBody = overlay("payload"),
    ): AppleGatewayCommand {
        val unsigned =
            AppleGatewayCommand(
                callerId = CALLER,
                requestId = "request-$clientMessageId",
                clientMessageId = clientMessageId,
                sourceChannelId = projection.sourceChannelId,
                routeToken = projection.routeToken,
                radioGeneration = projection.radioGeneration,
                issuedAtMillis = NOW,
                expiresAtMillis = NOW + AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS,
                keyVersion = 1,
                nonce = nonce,
                body = body,
            )
        return unsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(unsigned, KEY))
    }

    private fun overlay(payload: String) = AppleGatewayCommandBody.NtsocialEnvelope(
        rawEnvelope =
        NtsocialEnvelopeCodec.encode(
            headerMsgId = "00112233445566778899AABBCCDDEEFF".decodeHex(),
            payload = payload.encodeUtf8(),
        ),
    )

    private fun nonce(seed: Int): ByteString =
        ByteArray(AppleGatewayContract.COMMAND_NONCE_SIZE_BYTES) { index -> (seed + index).toByte() }.toByteString()

    private fun differentRouteToken(): String =
        ByteArray(AppleGatewayContract.ROUTE_TOKEN_SIZE_BYTES) { 0x7F.toByte() }.toByteString().base64Url().trimEnd('=')

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val CALLER = AppleGatewayContract.PARENT_CALLER_ID
        const val CLIENT_ID = "00112233445566778899AABBCCDDEEFF"
        const val OTHER_CLIENT_ID = "FFEEDDCCBBAA99887766554433221100"
        val KEY = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F".decodeHex()
    }
}

private class FakeRadioPort(private val events: MutableList<String>? = null) : AppleGatewayRadioPort {
    var snapshotValue =
        AppleGatewayRadioSnapshot(
            readiness = AppleGatewayReadiness.READY,
            channels = listOf(channel()),
            historyEpoch = "history-epoch",
            overlayHighWater = 0,
            nativeTextHighWater = 0,
        )
    var nextAdmission: AppleGatewayRadioAdmissionResult = AppleGatewayRadioAdmissionResult.Accepted
    val admissions = mutableListOf<Any>()

    override suspend fun snapshot(): AppleGatewayRadioSnapshot = snapshotValue

    override suspend fun durablyAdmitOverlay(
        admission: AppleGatewayOverlayAdmission,
    ): AppleGatewayRadioAdmissionResult {
        events?.add("radio:OVERLAY")
        admissions += admission
        return nextAdmission
    }

    override suspend fun durablyAdmitNativeText(
        admission: AppleGatewayNativeTextAdmission,
    ): AppleGatewayRadioAdmissionResult {
        events?.add("radio:NATIVE")
        admissions += admission
        return nextAdmission
    }
}

private class RecordingLedger(
    private val events: MutableList<String>,
    private var markAcceptedFailuresRemaining: Int = 0,
) : AppleGatewayLedger {
    private val records = mutableMapOf<Pair<String, String>, AppleGatewayLedgerRecord>()

    override suspend fun lookup(callerId: String, canonicalClientMessageId: String): AppleGatewayLedgerRecord? =
        records[callerId to canonicalClientMessageId]

    override suspend fun reserve(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
    ): AppleGatewayLedgerReservation {
        val key = callerId to canonicalClientMessageId
        val existing = records[key]
        val reservation =
            AppleGatewayIdempotencyPolicy.reserve(callerId, canonicalClientMessageId, requestFingerprint, existing)
        if (existing == null && reservation is AppleGatewayLedgerReservation.Pending) {
            events += "ledger:PENDING"
            records[key] =
                AppleGatewayLedgerRecord(
                    callerId,
                    canonicalClientMessageId,
                    requestFingerprint,
                    AppleGatewayLedgerState.PENDING,
                    reservation.packetId,
                    records.size.toLong() + 1,
                )
        }
        return reservation
    }

    override suspend fun markAccepted(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
        packetId: Int,
    ) {
        if (markAcceptedFailuresRemaining > 0) {
            markAcceptedFailuresRemaining -= 1
            events += "ledger:ACCEPTED_FAILED"
            error("injected private-ledger acceptance failure")
        }
        events += "ledger:ACCEPTED"
        val key = callerId to canonicalClientMessageId
        val existing = checkNotNull(records[key])
        require(existing.requestFingerprint == requestFingerprint && existing.packetId == packetId)
        records[key] = existing.copy(state = AppleGatewayLedgerState.ACCEPTED)
    }
}

private fun channel() = AppleGatewayRadioChannelIdentity(
    sourceChannelId = "meshtastic:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    slotIndex = 3,
    displayName = "Mesh",
    role = "SECONDARY",
    securityClass = "CUSTOM",
    capabilities = AppleGatewayRouteCapability.entries.toSet(),
)
