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
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppleGatewayContractTest {
    @Test
    fun `apple wake names are payload free contract constants`() {
        assertEquals(
            "com.ntsocial.meshlink.gateway.command-available",
            AppleGatewayContract.COMMAND_AVAILABLE_NOTIFICATION,
        )
        assertEquals("com.ntsocial.meshlink.gateway.state-changed", AppleGatewayContract.STATE_CHANGED_NOTIFICATION)
        assertEquals("ntsocial-meshlink://process", AppleGatewayContract.PROCESS_DEEP_LINK)
    }

    @Test
    fun `valid authenticated overlay command preserves exact envelope`() {
        val unsigned = command(body = AppleGatewayCommandBody.NtsocialEnvelope(rawEnvelope = envelope()))
        val command = unsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(unsigned, key))

        assertEquals(
            "000000216e74736f6369616c2d6170706c652d676174657761792d636f6d6d616e642d763100000001" +
                "0000001f53454e445f4e54534f4349414c5f454e56454c4f50455f544f5f524f55544500000010636f" +
                "6d2e6e74736f6369616c2e696f7300000009726571756573742d31000000203030313132323333343435" +
                "3536363737383839394141424243434444454546460000004b6d6573687461737469633a303132333435" +
                "363738396162636465663031323334353637383961626364656630313233343536373839616263646566" +
                "303132333435363738396162636465660000002b41414543417751464267634943516f4c4441304f4478" +
                "415245684d554652595847426b61477877644868380000002430313866356561392d393461662d376630" +
                "342d386663382d303132333435363738396162000001a3185c5000000001a3185e24c000000001000000" +
                "1000112233445566778899aabbccddeeff0000001a4e4d0100112233445566778899aabbccddeeff7061" +
                "796c6f6164ffffffff0000000001",
            AppleGatewayCommandCodec.canonicalAuthenticationBytes(unsigned).hex(),
        )
        assertEquals(
            "05393d1984327da39e681eb4542c66c213375d3cd21c09529d4414feeb20602a",
            command.authenticationTag.hex(),
        )

        val result = AppleGatewayCommandValidator.validate(command, route(), key, NOW)

        val valid = assertIs<AppleGatewayValidationResult.Valid>(result)
        assertEquals(CLIENT_ID, valid.canonicalClientMessageId)
        assertEquals(64, valid.requestFingerprint.length)
        assertEquals(envelope(), (command.body as AppleGatewayCommandBody.NtsocialEnvelope).rawEnvelope)
    }

    @Test
    fun `authentication covers route generation timing nonce and body`() {
        val unsigned = command(body = AppleGatewayCommandBody.NtsocialEnvelope(rawEnvelope = envelope()))
        val signed = unsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(unsigned, key))

        assertTrue(AppleGatewayAuthenticator.verify(signed, key))
        assertFalse(AppleGatewayAuthenticator.verify(signed.copy(radioGeneration = "new-generation"), key))
        assertFalse(
            AppleGatewayAuthenticator.verify(
                signed.copy(body = AppleGatewayCommandBody.NtsocialEnvelope(envelope(payload = "changed"))),
                key,
            ),
        )
    }

    @Test
    fun `stale route and expired command fail closed`() {
        val unsigned = command(body = AppleGatewayCommandBody.NtsocialEnvelope(rawEnvelope = envelope()))
        val signed = unsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(unsigned, key))

        assertEquals(
            AppleGatewayRejectionReason.INVALID_ROUTE,
            assertIs<AppleGatewayValidationResult.Invalid>(
                AppleGatewayCommandValidator.validate(
                    signed,
                    route().copy(radioGeneration = "old-generation"),
                    key,
                    NOW,
                ),
            )
                .reason,
        )
        assertEquals(
            AppleGatewayRejectionReason.EXPIRED,
            assertIs<AppleGatewayValidationResult.Invalid>(
                AppleGatewayCommandValidator.validate(signed, route(), key, NOW + 120_001),
            )
                .reason,
        )
    }

    @Test
    fun `command lifetime overflow and overlong windows fail closed`() {
        val body = AppleGatewayCommandBody.NtsocialEnvelope(rawEnvelope = envelope())
        val extremeUnsigned =
            command(body = body).copy(issuedAtMillis = Long.MIN_VALUE, expiresAtMillis = Long.MAX_VALUE)
        val extreme = extremeUnsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(extremeUnsigned, key))
        val overlongUnsigned =
            command(body = body).copy(expiresAtMillis = NOW + AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS + 1)
        val overlong = overlongUnsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(overlongUnsigned, key))

        assertEquals(
            AppleGatewayRejectionReason.INVALID_TIME_WINDOW,
            assertIs<AppleGatewayValidationResult.Invalid>(
                AppleGatewayCommandValidator.validate(extreme, route(), key, NOW),
            )
                .reason,
        )
        assertEquals(
            AppleGatewayRejectionReason.INVALID_TIME_WINDOW,
            assertIs<AppleGatewayValidationResult.Invalid>(
                AppleGatewayCommandValidator.validate(overlong, route(), key, NOW),
            )
                .reason,
        )
    }

    @Test
    fun `client id is exactly 32 hex and canonicalized uppercase`() {
        val unsigned =
            command(
                clientMessageId = CLIENT_ID.lowercase(),
                body = AppleGatewayCommandBody.NtsocialEnvelope(rawEnvelope = envelope()),
            )
        val signed = unsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(unsigned, key))
        assertEquals(
            CLIENT_ID,
            assertIs<AppleGatewayValidationResult.Valid>(
                AppleGatewayCommandValidator.validate(signed, route(), key, NOW),
            )
                .canonicalClientMessageId,
        )

        val invalid = signed.copy(clientMessageId = CLIENT_ID.dropLast(1))
        assertEquals(
            AppleGatewayRejectionReason.INVALID_CLIENT_MESSAGE_ID,
            assertIs<AppleGatewayValidationResult.Invalid>(
                AppleGatewayCommandValidator.validate(invalid, route(), key, NOW),
            )
                .reason,
        )
    }

    @Test
    fun `native text is broadcast-only and enforces utf8 limit`() {
        val validUnsigned = command(body = AppleGatewayCommandBody.NativeBroadcastText("訊".repeat(60)))
        val valid = validUnsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(validUnsigned, key))
        assertIs<AppleGatewayValidationResult.Valid>(AppleGatewayCommandValidator.validate(valid, route(), key, NOW))

        val tooLargeUnsigned = command(body = AppleGatewayCommandBody.NativeBroadcastText("訊".repeat(61)))
        val tooLarge = tooLargeUnsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(tooLargeUnsigned, key))
        assertEquals(
            AppleGatewayRejectionReason.INVALID_NATIVE_TEXT,
            assertIs<AppleGatewayValidationResult.Invalid>(
                AppleGatewayCommandValidator.validate(tooLarge, route(), key, NOW),
            )
                .reason,
        )
    }

    @Test
    fun `legacy port is inbound only and malformed payload is excluded`() {
        assertTrue(AppleGatewayOverlayIngressPolicy.accepts(NtsocialTransport.PRIVATE_APP_PORT_NUM, envelope()))
        assertTrue(AppleGatewayOverlayIngressPolicy.accepts(NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM, envelope()))
        assertFalse(AppleGatewayOverlayIngressPolicy.accepts(42, envelope()))
        assertFalse(
            AppleGatewayOverlayIngressPolicy.accepts(
                NtsocialTransport.PRIVATE_APP_PORT_NUM,
                "not-an-envelope".encodeUtf8(),
            ),
        )
    }

    @Test
    fun `idempotency distinguishes replay conflict and accepted state`() {
        val fingerprint = "A".repeat(AppleGatewayContract.REQUEST_FINGERPRINT_HEX_LENGTH)
        val packetId = AppleGatewayIdempotencyPolicy.deterministicPacketId(CALLER, CLIENT_ID)
        val pending =
            AppleGatewayLedgerRecord(CALLER, CLIENT_ID, fingerprint, AppleGatewayLedgerState.PENDING, packetId, 1)
        val accepted = pending.copy(state = AppleGatewayLedgerState.ACCEPTED)

        assertEquals(
            AppleGatewayLedgerReservation.Pending(packetId),
            AppleGatewayIdempotencyPolicy.reserve(CALLER, CLIENT_ID, fingerprint, pending),
        )
        assertEquals(
            AppleGatewayLedgerReservation.Accepted(packetId),
            AppleGatewayIdempotencyPolicy.reserve(CALLER, CLIENT_ID, fingerprint, accepted),
        )
        assertEquals(
            AppleGatewayLedgerReservation.Conflict,
            AppleGatewayIdempotencyPolicy.reserve(CALLER, CLIENT_ID, "B".repeat(64), accepted),
        )
        assertNotEquals(0, packetId)
    }

    @Test
    fun `ledger retention is insertion ordered and bounded per caller`() {
        val records =
            (0..AppleGatewayContract.MAX_LEDGER_RECORDS_PER_CALLER).map { index ->
                AppleGatewayLedgerRecord(
                    callerId = CALLER,
                    clientMessageId = index.toString(16).padStart(32, '0').uppercase(),
                    requestFingerprint = "A".repeat(64),
                    state = AppleGatewayLedgerState.PENDING,
                    packetId = index + 1,
                    insertionSequence = index.toLong(),
                )
            }

        val trimmed = AppleGatewayIdempotencyPolicy.trim(records)
        assertEquals(AppleGatewayContract.MAX_LEDGER_RECORDS_PER_CALLER, trimmed.size)
        assertEquals(1, trimmed.first().insertionSequence)
        assertEquals(AppleGatewayContract.MAX_LEDGER_RECORDS_PER_CALLER.toLong(), trimmed.last().insertionSequence)
    }

    private fun command(clientMessageId: String = CLIENT_ID, body: AppleGatewayCommandBody) = AppleGatewayCommand(
        callerId = CALLER,
        requestId = "request-1",
        clientMessageId = clientMessageId,
        sourceChannelId = SOURCE_CHANNEL_ID,
        routeToken = ROUTE_TOKEN,
        radioGeneration = GENERATION,
        issuedAtMillis = NOW,
        expiresAtMillis = NOW + AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS,
        keyVersion = 1,
        nonce = "00112233445566778899AABBCCDDEEFF".decodeHex(),
        body = body,
    )

    private fun route() = AppleGatewayRoute(
        callerId = CALLER,
        sourceChannelId = SOURCE_CHANNEL_ID,
        capturedSlotIndex = 3,
        capabilities = AppleGatewayRouteCapability.entries.toSet(),
        token = ROUTE_TOKEN,
        radioGeneration = GENERATION,
        expiresAtMillis = NOW + AppleGatewayContract.ROUTE_TTL_MILLIS,
    )

    private fun envelope(payload: String = "payload"): ByteString = NtsocialEnvelopeCodec.encode(
        headerMsgId = "00112233445566778899AABBCCDDEEFF".decodeHex(),
        payload = payload.encodeUtf8(),
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val CALLER = AppleGatewayContract.PARENT_CALLER_ID
        const val CLIENT_ID = "00112233445566778899AABBCCDDEEFF"
        const val SOURCE_CHANNEL_ID = "meshtastic:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val GENERATION = "018f5ea9-94af-7f04-8fc8-0123456789ab"
        val ROUTE_TOKEN =
            requireNotNull("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8".decodeBase64()).base64Url().trimEnd('=')
        val key = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F".decodeHex()
    }
}
