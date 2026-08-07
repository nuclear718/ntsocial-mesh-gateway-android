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

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleGatewayStoreTest {
    private val directory = Files.createTempDirectory("meshlink-apple-gateway-")
    private val store = AppleGatewayStore(directory.resolve(AppleGatewaySchema.FILE_NAME).toString())

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `schema and projection survive reopen with duplicate source ids in different slots`() = runTest {
        val status = status()
        val channels = listOf(channel(0), channel(1))

        store.initialize()
        store.replaceProjection(status, channels)
        val reopened = AppleGatewayStore(directory.resolve(AppleGatewaySchema.FILE_NAME).toString())

        assertEquals(status, reopened.readStatus())
        assertEquals(channels, reopened.readChannels())
    }

    @Test
    fun `command insert claim result and nonce reservation are durable and idempotent`() = runTest {
        store.initialize()
        val unsigned = command()
        val command = unsigned.copy(authenticationTag = AppleGatewayAuthenticator.tag(unsigned, KEY))

        assertTrue(store.enqueueCommand(command, NOW))
        assertFalse(store.enqueueCommand(command, NOW + 1))
        assertEquals(command, store.claimNextCommand(PROVIDER, NOW + 2))
        assertNull(store.claimNextCommand(PROVIDER, NOW + 3))
        assertTrue(store.reserveNonce(command, NOW))
        assertFalse(store.reserveNonce(command, NOW + 1))

        val result =
            AppleGatewayCommandResult(
                callerId = CALLER,
                clientMessageId = CLIENT_ID,
                resultSequence = 1,
                state = AppleGatewayCommandResultState.ACCEPTED_LOCAL,
                packetId = 123,
                reason = null,
                updatedAtMillis = NOW + 4,
            )
        store.appendResult(result)
        assertEquals(listOf(result), store.readResults(CALLER, CLIENT_ID.lowercase()))
    }

    @Test
    fun `overlay cursor is epoch scoped ordered and bounded`() = runTest {
        store.initialize()
        val count = AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS + 3
        repeat(count) { index ->
            assertTrue(
                store.appendOverlayIngress(
                    AppleGatewayOverlayIngress(
                        historyEpoch = EPOCH,
                        changeSequence = (index + 1).toLong(),
                        sourceChannelId = SOURCE_CHANNEL,
                        sourceMessageId = index.toString(16).padStart(32, '0').uppercase(),
                        sourceNodeId = "!12345678",
                        packetId = index.toUInt(),
                        portNumber = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                        rawEnvelope = envelope("payload-$index"),
                        receivedAtMillis = NOW + index,
                    ),
                ),
            )
        }

        val page = store.readOverlayIngress(EPOCH, 0, AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS)
        assertEquals(AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS, page.size)
        assertEquals(4, page.first().changeSequence)
        assertEquals(count.toLong(), page.last().changeSequence)
        assertTrue(store.readOverlayIngress("different-epoch", 0, 10).isEmpty())
    }

    @Test
    fun `next overlay sequence is allocated transactionally across reopen and accepts only gateway ingress ports`() =
        runTest {
            val first = store.appendNextOverlayIngress(EPOCH, overlayPayload(1, NtsocialTransport.PRIVATE_APP_PORT_NUM))
            val reopened = AppleGatewayStore(directory.resolve(AppleGatewaySchema.FILE_NAME).toString())
            val second =
                reopened.appendNextOverlayIngress(
                    EPOCH,
                    overlayPayload(2, NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM),
                )

            assertEquals(1, first.changeSequence)
            assertEquals(2, second.changeSequence)
            assertEquals(listOf(first, second), reopened.readOverlayIngress(EPOCH, 0, 10))
            assertEquals(2, reopened.readOverlayHighWater(EPOCH))
            assertEquals(0, reopened.readOverlayHighWater("other-epoch"))
            assertFailsWith<IllegalArgumentException> {
                reopened.appendNextOverlayIngress(EPOCH, overlayPayload(3, 42))
            }
        }

    @Test
    fun `overlay sequence remains monotonic after another epoch evicts every retained row`() = runTest {
        repeat(AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS) { index ->
            store.appendNextOverlayIngress(EPOCH, overlayPayload(index + 1, NtsocialTransport.PRIVATE_APP_PORT_NUM))
        }
        repeat(AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS) { index ->
            store.appendNextOverlayIngress(
                OTHER_EPOCH,
                overlayPayload(index + 1, NtsocialTransport.PRIVATE_APP_PORT_NUM)
                    .copy(receivedAtMillis = NOW + 10_000 + index),
            )
        }

        assertTrue(store.readOverlayIngress(EPOCH, 0, AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS).isEmpty())
        assertEquals(AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS.toLong(), store.readOverlayHighWater(EPOCH))

        val resumed =
            store.appendNextOverlayIngress(
                EPOCH,
                overlayPayload(999, NtsocialTransport.PRIVATE_APP_PORT_NUM).copy(receivedAtMillis = NOW + 20_000),
            )

        assertEquals(AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS + 1L, resumed.changeSequence)
        assertEquals(resumed.changeSequence, store.readOverlayHighWater(EPOCH))
        assertEquals(listOf(resumed), store.readOverlayIngress(EPOCH, 0, 10))
    }

    @Test
    fun `schema v1 migration backfills overlay epoch high water from retained ingress`() = runTest {
        store.appendOverlayIngress(
            AppleGatewayOverlayIngress(
                historyEpoch = EPOCH,
                changeSequence = 42,
                sourceChannelId = SOURCE_CHANNEL,
                sourceMessageId = CLIENT_ID,
                sourceNodeId = "!12345678",
                packetId = 42u,
                portNumber = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                rawEnvelope = envelope("legacy-v1"),
                receivedAtMillis = NOW,
            ),
        )
        BundledSQLiteDriver()
            .open(
                directory.resolve(AppleGatewaySchema.FILE_NAME).toString(),
                SQLITE_OPEN_READWRITE or SQLITE_OPEN_FULLMUTEX,
            )
            .use { connection -> connection.prepare("DROP TABLE overlay_epoch_state").use { it.step() } }

        val reopened = AppleGatewayStore(directory.resolve(AppleGatewaySchema.FILE_NAME).toString())

        assertEquals(42L, reopened.readOverlayHighWater(EPOCH))
        val next = reopened.appendNextOverlayIngress(EPOCH, overlayPayload(43, NtsocialTransport.PRIVATE_APP_PORT_NUM))
        assertEquals(43L, next.changeSequence)
    }

    @Test
    fun `native message cursor is epoch scoped paged and permits duplicate stable source identity`() = runTest {
        val first = nativeChange(sequence = 1, packetId = 10u, text = "first")
        val duplicateIdentity = nativeChange(sequence = 2, packetId = 11u, text = "duplicate row")
        val otherEpoch = nativeChange(sequence = 1, packetId = 12u, text = "other").copy(historyEpoch = "other")

        assertTrue(store.appendNativeMessageChange(first))
        assertTrue(store.appendNativeMessageChange(duplicateIdentity))
        assertTrue(store.appendNativeMessageChange(otherEpoch))
        assertFalse(store.appendNativeMessageChange(first))

        assertEquals(listOf(first), store.readNativeMessageChanges(EPOCH, after = 0, limit = 1))
        assertEquals(listOf(duplicateIdentity), store.readNativeMessageChanges(EPOCH, after = 1, limit = 10))
        assertEquals(listOf(otherEpoch), store.readNativeMessageChanges("other", after = 0))
        assertEquals(2, store.readNativeMessageHighWater(EPOCH))
        assertEquals(1, store.readNativeMessageHighWater("other"))
        assertEquals(0, store.readNativeMessageHighWater("missing"))
        assertFailsWith<IllegalArgumentException> {
            store.readNativeMessageChanges(
                EPOCH,
                after = 0,
                limit = AppleGatewayContract.MAX_NATIVE_MESSAGE_CHANGE_PAGE_SIZE + 1,
            )
        }
    }

    @Test
    fun `claims release expire and are immediately reclaimed by a new provider process`() = runTest {
        val command = command().let { it.copy(authenticationTag = AppleGatewayAuthenticator.tag(it, KEY)) }
        store.enqueueCommand(command, NOW)

        assertEquals(command, store.claimNextCommand("provider-a", NOW))
        assertNull(store.claimNextCommand("provider-a", NOW + 1))
        assertEquals(
            command,
            store.claimNextCommand("provider-a", NOW + AppleGatewayContract.COMMAND_CLAIM_RECLAIM_MILLIS + 1),
        )
        assertTrue(store.releaseClaim(CALLER, CLIENT_ID, "provider-a"))
        assertEquals(command, store.claimNextCommand("provider-a", NOW + 2))
        assertEquals(command, store.claimNextCommand("provider-b", NOW + 3))

        store.appendNextResult(
            callerId = CALLER,
            clientMessageId = CLIENT_ID,
            state = AppleGatewayCommandResultState.REJECTED,
            packetId = null,
            reason = AppleGatewayRejectionReason.INVALID_ROUTE,
            updatedAtMillis = NOW + 4,
        )
        assertNull(store.claimNextCommand("provider-c", NOW + 5))
    }

    @Test
    fun `caller reset clears caller mailbox nonce and projected routes but leaves status`() = runTest {
        val caller =
            AppleGatewayCallerProjection(
                callerId = CALLER,
                activeKeyVersion = 1,
                revoked = false,
                lastSeenAtMillis = NOW,
            )
        store.replaceProjection(status(), listOf(channel(0)), caller)
        val command = command().let { it.copy(authenticationTag = AppleGatewayAuthenticator.tag(it, KEY)) }
        store.enqueueCommand(command, NOW)
        store.reserveNonce(command, NOW)

        assertEquals(caller, store.readCallerProjection(CALLER))
        store.resetCallerProjection(CALLER)

        assertNull(store.readCallerProjection(CALLER))
        assertTrue(store.readChannels().isEmpty())
        assertNull(store.claimNextCommand(PROVIDER, NOW))
        assertNotNull(store.readStatus())
        assertTrue(store.reserveNonce(command, NOW + 1))
    }

    @Test
    fun `reset removes shared projections and mailbox without touching private state`() = runTest {
        store.replaceProjection(status(), listOf(channel(0)))
        val command = command().let { it.copy(authenticationTag = AppleGatewayAuthenticator.tag(it, KEY)) }
        store.enqueueCommand(command, NOW)
        store.appendNextOverlayIngress(EPOCH, overlayPayload(1, NtsocialTransport.PRIVATE_APP_PORT_NUM))
        store.appendNativeMessageChange(nativeChange(sequence = 1, packetId = 1u, text = "native"))

        store.resetSharedGateway()

        assertNull(store.readStatus())
        assertTrue(store.readChannels().isEmpty())
        assertNull(store.claimNextCommand(PROVIDER, NOW))
        assertEquals(0L, store.readOverlayHighWater(EPOCH))
        assertTrue(store.readNativeMessageChanges(EPOCH, 0).isEmpty())
    }

    private fun status() = AppleGatewayStatus(
        providerInstanceId = PROVIDER,
        readiness = AppleGatewayReadiness.READY,
        radioGeneration = GENERATION,
        historyEpoch = EPOCH,
        overlayHighWater = 7,
        nativeTextHighWater = 9,
        activeKeyVersion = 1,
        updatedAtMillis = NOW,
    )

    private fun channel(slot: Int) = AppleGatewayChannelProjection(
        sourceChannelId = SOURCE_CHANNEL,
        slotIndex = slot,
        displayName = "Channel $slot",
        role = if (slot == 0) "PRIMARY" else "SECONDARY",
        securityClass = "CUSTOM",
        capabilities = setOf(AppleGatewayRouteCapability.SEND_NTSOCIAL_ENVELOPE),
        routeToken = ROUTE_TOKEN,
        routeExpiresAtMillis = NOW + AppleGatewayContract.ROUTE_TTL_MILLIS,
        radioGeneration = GENERATION,
    )

    private fun command() = AppleGatewayCommand(
        callerId = CALLER,
        requestId = "request-1",
        clientMessageId = CLIENT_ID,
        sourceChannelId = SOURCE_CHANNEL,
        routeToken = ROUTE_TOKEN,
        radioGeneration = GENERATION,
        issuedAtMillis = NOW,
        expiresAtMillis = NOW + AppleGatewayContract.COMMAND_MAX_LIFETIME_MILLIS,
        keyVersion = 1,
        nonce = "00112233445566778899AABBCCDDEEFF".decodeHex(),
        body = AppleGatewayCommandBody.NtsocialEnvelope(envelope()),
    )

    private fun envelope(payload: String = "payload") = NtsocialEnvelopeCodec.encode(
        headerMsgId = "00112233445566778899AABBCCDDEEFF".decodeHex(),
        payload = payload.encodeUtf8(),
    )

    private fun overlayPayload(index: Int, portNumber: Int) = AppleGatewayOverlayIngressPayload(
        sourceChannelId = SOURCE_CHANNEL,
        sourceMessageId = index.toString(16).padStart(32, '0').uppercase(),
        sourceNodeId = "!12345678",
        packetId = index.toUInt(),
        portNumber = portNumber,
        rawEnvelope = envelope("payload-$index"),
        receivedAtMillis = NOW + index,
    )

    private fun nativeChange(sequence: Long, packetId: UInt, text: String) = AppleGatewayNativeMessageChange(
        historyEpoch = EPOCH,
        changeSequence = sequence,
        sourceChannelId = SOURCE_CHANNEL,
        sourceMessageId = CLIENT_ID,
        fromNodeId = "!12345678",
        packetId = packetId,
        text = text,
        receivedAtMillis = NOW + sequence,
        originClientMessageId = CLIENT_ID,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val PROVIDER = "provider-instance"
        const val CALLER = AppleGatewayContract.PARENT_CALLER_ID
        const val CLIENT_ID = "00112233445566778899AABBCCDDEEFF"
        const val GENERATION = "018f5ea9-94af-7f04-8fc8-0123456789ab"
        const val EPOCH = "history-epoch"
        const val OTHER_EPOCH = "history-epoch-b"
        const val SOURCE_CHANNEL = "meshtastic:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val ROUTE_TOKEN =
            assertNotNull("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8".decodeBase64()).base64Url().trimEnd('=')
        val KEY = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F".decodeHex()
    }
}
