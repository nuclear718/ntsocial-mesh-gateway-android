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
package com.ntsocial.meshlink.core.service

import android.content.Context
import org.koin.core.annotation.Single
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Caller-bound opaque routes plus a bounded durable idempotency ledger for Gateway v2 overlay commands. */
@Single
internal class NtsocialGatewayRouteTokenStore(context: Context) {
    private val random = SecureRandom()
    private val lock = Any()
    private val routes = mutableMapOf<String, Route>()
    private val ledgerPreferences = context.getSharedPreferences(LEDGER_PREFERENCES, Context.MODE_PRIVATE)
    private val clientMessages = loadClientMessages()
    internal var persistClientMessagesOverride: (() -> Boolean)? = null

    fun issue(
        caller: NtsocialGatewayCaller,
        sourceChannelId: String,
        channelIndex: Int,
        radioGeneration: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String = synchronized(lock) {
        purgeExpired(nowMillis)
        val token = newGatewayRouteToken(random, TOKEN_SIZE_BYTES)
        routes[token] =
            Route(
                caller = caller,
                endpointId = null,
                sourceChannelId = sourceChannelId,
                channelIndex = channelIndex,
                radioGeneration = radioGeneration,
                fleetGeneration = null,
                expiresAtMillis = nowMillis + ROUTE_TTL_MILLIS,
            )
        token
    }

    fun resolve(
        token: String,
        caller: NtsocialGatewayCaller,
        sourceChannelId: String,
        radioGeneration: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ResolvedRoute? = synchronized(lock) {
        purgeExpired(nowMillis)
        routes[token]
            ?.takeIf {
                it.caller == caller &&
                    it.sourceChannelId == sourceChannelId &&
                    it.radioGeneration == radioGeneration
            }
            ?.let { ResolvedRoute(channelIndex = it.channelIndex) }
    }

    fun issueV3(
        caller: NtsocialGatewayCaller,
        endpointId: String,
        sourceChannelId: String,
        channelIndex: Int,
        endpointGeneration: String,
        fleetGeneration: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String = synchronized(lock) {
        require(isValidGatewayEndpointId(endpointId))
        purgeExpired(nowMillis)
        val token = newGatewayRouteToken(random, TOKEN_SIZE_BYTES)
        routes[token] =
            Route(
                caller = caller,
                endpointId = endpointId,
                sourceChannelId = sourceChannelId,
                channelIndex = channelIndex,
                radioGeneration = endpointGeneration,
                fleetGeneration = fleetGeneration,
                expiresAtMillis = nowMillis + ROUTE_TTL_MILLIS,
            )
        token
    }

    fun resolveV3(
        token: String,
        caller: NtsocialGatewayCaller,
        endpointId: String,
        sourceChannelId: String,
        endpointGeneration: String,
        fleetGeneration: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ResolvedRoute? = synchronized(lock) {
        purgeExpired(nowMillis)
        routes[token]
            ?.takeIf {
                it.caller == caller &&
                    it.endpointId == endpointId &&
                    it.sourceChannelId == sourceChannelId &&
                    it.radioGeneration == endpointGeneration &&
                    it.fleetGeneration == fleetGeneration
            }
            ?.let {
                ResolvedRoute(
                    channelIndex = it.channelIndex,
                    endpointId = it.endpointId,
                    endpointGeneration = it.radioGeneration,
                    fleetGeneration = it.fleetGeneration,
                )
            }
    }

    fun reserveClientMessage(
        caller: NtsocialGatewayCaller,
        clientMessageId: String,
        requestFingerprint: String,
        endpointId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): ClientMessageReservation = synchronized(lock) {
        val key = ClientMessageKey(caller.packageName, endpointId, clientMessageId)
        clientMessages[key]?.let { existing ->
            return@synchronized when {
                existing.requestFingerprint != requestFingerprint -> ClientMessageReservation.Conflict

                existing.state == ClientMessageState.ACCEPTED ->
                    ClientMessageReservation.Accepted(existing.packetId)

                else -> ClientMessageReservation.Pending(existing.packetId)
            }
        }

        val record =
            ClientMessageRecord(
                packetId = deterministicPacketId(key),
                requestFingerprint = requestFingerprint,
                state = ClientMessageState.PENDING,
                createdAtMillis = nowMillis,
            )
        val previous = LinkedHashMap(clientMessages)
        clientMessages[key] = record
        trimClientMessages()
        if (!persistClientMessages()) {
            clientMessages.clear()
            clientMessages.putAll(previous)
            error("Unable to persist Gateway client-message reservation")
        }
        ClientMessageReservation.Pending(record.packetId)
    }

    fun markClientMessageAccepted(
        caller: NtsocialGatewayCaller,
        clientMessageId: String,
        requestFingerprint: String,
        packetId: Int,
        endpointId: String? = null,
    ): Boolean = synchronized(lock) {
        val key = ClientMessageKey(caller.packageName, endpointId, clientMessageId)
        val existing = clientMessages[key]
        check(
            existing != null && existing.requestFingerprint == requestFingerprint && existing.packetId == packetId,
        ) {
            "Gateway client-message acceptance does not match its durable reservation"
        }
        clientMessages[key] = existing.copy(state = ClientMessageState.ACCEPTED)
        persistClientMessages().also { committed -> if (!committed) clientMessages[key] = existing }
    }

    private fun purgeExpired(nowMillis: Long) {
        routes.entries.removeAll { (_, route) -> route.expiresAtMillis <= nowMillis }
    }

    private fun deterministicPacketId(key: ClientMessageKey): Int {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(
                    (
                        key.endpointId?.let { endpointId -> "${key.packageName}:$endpointId:${key.clientMessageId}" }
                            ?: "${key.packageName}:${key.clientMessageId}"
                        )
                        .toByteArray(StandardCharsets.UTF_8),
                )
        val value =
            ((digest[0].toInt() and POSITIVE_FIRST_BYTE_MASK) shl BITS_24) or
                ((digest[1].toInt() and UNSIGNED_BYTE_MASK) shl BITS_16) or
                ((digest[2].toInt() and UNSIGNED_BYTE_MASK) shl BITS_8) or
                (digest[PACKET_ID_LAST_BYTE_INDEX].toInt() and UNSIGNED_BYTE_MASK)
        return value.takeUnless { it == 0 } ?: 1
    }

    private fun trimClientMessages() {
        while (clientMessages.size > MAX_CLIENT_MESSAGES) {
            clientMessages.remove(clientMessages.keys.first())
        }
    }

    private fun persistClientMessages(): Boolean = persistClientMessagesOverride?.invoke()
        ?: ledgerPreferences
            .edit()
            .clear()
            .also { editor ->
                clientMessages.forEach { (key, record) ->
                    editor.putString(key.persistedKey(), record.persistedValue())
                }
            }
            .commit()

    private fun loadClientMessages(): LinkedHashMap<ClientMessageKey, ClientMessageRecord> = ledgerPreferences.all
        .mapNotNull { (key, value) ->
            val clientKey = ClientMessageKey.fromPersistedKey(key)
            val record = (value as? String)?.let(ClientMessageRecord::fromPersistedValue)
            if (clientKey == null || record == null) null else clientKey to record
        }
        .sortedBy { (_, record) -> record.createdAtMillis }
        .takeLast(MAX_CLIENT_MESSAGES)
        .toMap(linkedMapOf())

    private data class Route(
        val caller: NtsocialGatewayCaller,
        val endpointId: String?,
        val sourceChannelId: String,
        val channelIndex: Int,
        val radioGeneration: String,
        val fleetGeneration: String?,
        val expiresAtMillis: Long,
    )

    private data class ClientMessageKey(val packageName: String, val endpointId: String?, val clientMessageId: String) {
        fun persistedKey(): String =
            listOfNotNull(packageName, endpointId, clientMessageId).joinToString(LEDGER_SEPARATOR.toString())

        companion object {
            fun fromPersistedKey(value: String): ClientMessageKey? {
                val parts = value.split(LEDGER_SEPARATOR)
                return when {
                    parts.size == 2 && parts.all(String::isNotBlank) -> ClientMessageKey(parts[0], null, parts[1])

                    parts.size == FLEET_LEDGER_KEY_PARTS &&
                        parts.all(String::isNotBlank) &&
                        isValidGatewayEndpointId(parts[1]) -> ClientMessageKey(parts[0], parts[1], parts[2])

                    else -> null
                }
            }
        }
    }

    private data class ClientMessageRecord(
        val packetId: Int,
        val requestFingerprint: String,
        val state: ClientMessageState,
        val createdAtMillis: Long,
    ) {
        fun persistedValue(): String =
            listOf(packetId, requestFingerprint, state.name, createdAtMillis).joinToString(LEDGER_SEPARATOR.toString())

        companion object {
            fun fromPersistedValue(value: String): ClientMessageRecord? {
                val parts = value.split(LEDGER_SEPARATOR)
                return if (parts.size != LEDGER_VALUE_PARTS) {
                    null
                } else {
                    val packetId = parts[0].toIntOrNull()?.takeIf { it > 0 }
                    val fingerprint = parts[1].takeIf { it.length == REQUEST_FINGERPRINT_HEX_LENGTH }
                    val state = runCatching { ClientMessageState.valueOf(parts[2]) }.getOrNull()
                    val createdAtMillis = parts[LEDGER_CREATED_AT_INDEX].toLongOrNull()
                    if (packetId == null || fingerprint == null || state == null || createdAtMillis == null) {
                        null
                    } else {
                        ClientMessageRecord(packetId, fingerprint, state, createdAtMillis)
                    }
                }
            }
        }
    }

    private enum class ClientMessageState {
        PENDING,
        ACCEPTED,
    }

    sealed interface ClientMessageReservation {
        data class Pending(val packetId: Int) : ClientMessageReservation

        data class Accepted(val packetId: Int) : ClientMessageReservation

        data object Conflict : ClientMessageReservation
    }

    data class ResolvedRoute(
        val channelIndex: Int,
        val endpointId: String? = null,
        val endpointGeneration: String? = null,
        val fleetGeneration: String? = null,
    )

    private companion object {
        const val LEDGER_PREFERENCES = "ntsocial_gateway_v2_client_messages"
        const val LEDGER_SEPARATOR = '|'
        const val LEDGER_VALUE_PARTS = 4
        const val FLEET_LEDGER_KEY_PARTS = 3
        const val REQUEST_FINGERPRINT_HEX_LENGTH = 64
        const val ROUTE_TTL_MILLIS = 120_000L
        const val TOKEN_SIZE_BYTES = 32
        const val MAX_CLIENT_MESSAGES = 256
        const val POSITIVE_FIRST_BYTE_MASK = 0x7F
        const val UNSIGNED_BYTE_MASK = 0xFF
        const val BITS_8 = 8
        const val BITS_16 = 16
        const val BITS_24 = 24
        const val PACKET_ID_LAST_BYTE_INDEX = 3
        const val LEDGER_CREATED_AT_INDEX = 3
    }
}

private fun newGatewayRouteToken(random: SecureRandom, tokenSizeBytes: Int): String {
    val bytes = ByteArray(tokenSizeBytes)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
