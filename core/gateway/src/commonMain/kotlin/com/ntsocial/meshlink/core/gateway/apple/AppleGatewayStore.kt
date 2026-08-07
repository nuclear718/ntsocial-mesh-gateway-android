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
@file:Suppress("LargeClass", "LongMethod", "MagicNumber", "TooGenericExceptionCaught", "TooManyFunctions")

package com.ntsocial.meshlink.core.gateway.apple

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayNativeText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString

/**
 * Small explicit SQLite mailbox for the Apple App Group.
 *
 * A fresh FULLMUTEX connection is used for each operation. The in-process [mutex] prevents this instance from stacking
 * transactions while WAL and SQLite locking coordinate the parent and companion processes.
 */
class AppleGatewayStore(private val databasePath: String) {
    private val driver = BundledSQLiteDriver()
    private val mutex = Mutex()

    suspend fun initialize() = mutex.withLock { withConnection { connection -> migrate(connection) } }

    suspend fun replaceProjection(
        status: AppleGatewayStatus,
        channels: List<AppleGatewayChannelProjection>,
        caller: AppleGatewayCallerProjection? = null,
    ) = transaction { connection ->
        require(status.schemaVersion == AppleGatewayContract.SCHEMA_VERSION)
        require(channels.all { it.radioGeneration == status.radioGeneration })
        connection.execute("DELETE FROM channel_projection")
        connection
            .prepare(
                """
                INSERT INTO gateway_meta(
                    singleton_id, schema_version, provider_instance_id, readiness, radio_generation, history_epoch,
                    overlay_high_water, native_text_high_water, active_key_version, updated_at_millis
                ) VALUES(1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(singleton_id) DO UPDATE SET
                    schema_version=excluded.schema_version,
                    provider_instance_id=excluded.provider_instance_id,
                    readiness=excluded.readiness,
                    radio_generation=excluded.radio_generation,
                    history_epoch=excluded.history_epoch,
                    overlay_high_water=excluded.overlay_high_water,
                    native_text_high_water=excluded.native_text_high_water,
                    active_key_version=excluded.active_key_version,
                    updated_at_millis=excluded.updated_at_millis
                """
                    .trimIndent(),
            )
            .use { statement ->
                statement.bindInt(1, status.schemaVersion)
                statement.bindText(2, status.providerInstanceId)
                statement.bindText(3, status.readiness.name)
                statement.bindText(4, status.radioGeneration)
                statement.bindNullableText(5, status.historyEpoch)
                statement.bindLong(6, status.overlayHighWater)
                statement.bindLong(7, status.nativeTextHighWater)
                statement.bindInt(8, status.activeKeyVersion)
                statement.bindLong(9, status.updatedAtMillis)
                statement.step()
            }
        channels.forEach { channel -> connection.insertChannel(channel) }
        if (caller != null) connection.upsertCallerProjection(caller)
    }

    suspend fun readCallerProjection(callerId: String): AppleGatewayCallerProjection? = mutex.withLock {
        withInitializedConnection { connection ->
            connection
                .prepare(
                    """
                        SELECT caller_id, active_key_version, revoked, last_seen_at_millis
                        FROM gateway_caller_projection WHERE caller_id=?
                        """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, callerId)
                    if (statement.step()) {
                        AppleGatewayCallerProjection(
                            callerId = statement.getText(0),
                            activeKeyVersion = statement.getInt(1),
                            revoked = statement.getBoolean(2),
                            lastSeenAtMillis = statement.getLong(3),
                        )
                    } else {
                        null
                    }
                }
        }
    }

    suspend fun readStatus(): AppleGatewayStatus? = mutex.withLock {
        withInitializedConnection { connection ->
            connection
                .prepare(
                    """
                        SELECT schema_version, provider_instance_id, readiness, radio_generation, history_epoch,
                               overlay_high_water, native_text_high_water, active_key_version, updated_at_millis
                        FROM gateway_meta WHERE singleton_id=1
                        """
                        .trimIndent(),
                )
                .use { statement -> if (statement.step()) statement.readStatus() else null }
        }
    }

    suspend fun readChannels(): List<AppleGatewayChannelProjection> = mutex.withLock {
        withInitializedConnection { connection ->
            connection
                .prepare(
                    """
                        SELECT source_channel_id, slot_index, display_name, role, security_class, capabilities,
                               route_token, route_expires_at_millis, radio_generation
                        FROM channel_projection ORDER BY slot_index
                        """
                        .trimIndent(),
                )
                .use { statement -> buildList { while (statement.step()) add(statement.readChannel()) } }
        }
    }

    /** Parent-side immutable insert. Returns false for an already present caller/client ID. */
    suspend fun enqueueCommand(command: AppleGatewayCommand, receivedAtMillis: Long): Boolean =
        transaction { connection ->
            connection
                .prepare(
                    """
                    INSERT OR IGNORE INTO command_inbox(
                        caller_id, client_message_id, schema_version, request_id, source_channel_id, route_token,
                        radio_generation, issued_at_millis, expires_at_millis, key_version, nonce, command_type,
                        body_payload, destination, hop_limit, want_ack, authentication_tag, received_at_millis
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindCommand(command, receivedAtMillis)
                    statement.step()
                }
            connection.changedRows() == 1L
        }

    /**
     * Companion-side compare-and-set claim.
     *
     * A new provider process immediately reclaims claims owned by the old process. The same process can reclaim only
     * after [reclaimAfterMillis], and terminal results are never claimed again.
     */
    suspend fun claimNextCommand(
        providerInstanceId: String,
        claimedAtMillis: Long,
        reclaimAfterMillis: Long = AppleGatewayContract.COMMAND_CLAIM_RECLAIM_MILLIS,
    ): AppleGatewayCommand? = transaction { connection ->
        require(providerInstanceId.isNotBlank())
        require(reclaimAfterMillis >= 0)
        val reclaimBeforeMillis = claimedAtMillis - reclaimAfterMillis
        val command =
            connection
                .prepare(
                    """
                    SELECT c.schema_version, c.caller_id, c.request_id, c.client_message_id, c.source_channel_id,
                           c.route_token, c.radio_generation, c.issued_at_millis, c.expires_at_millis, c.key_version,
                           c.nonce, c.command_type, c.body_payload, c.destination, c.hop_limit, c.want_ack,
                           c.authentication_tag
                    FROM command_inbox c
                    LEFT JOIN command_claim q
                      ON q.caller_id=c.caller_id AND q.client_message_id=c.client_message_id
                    WHERE NOT EXISTS (
                        SELECT 1 FROM command_result r
                        WHERE r.caller_id=c.caller_id AND r.client_message_id=c.client_message_id
                          AND r.state IN (?, ?)
                    )
                      AND (
                        q.client_message_id IS NULL
                        OR q.provider_instance_id != ?
                        OR q.claimed_at_millis <= ?
                      )
                    ORDER BY c.received_at_millis, c.caller_id, c.client_message_id
                    LIMIT 1
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, AppleGatewayCommandResultState.ACCEPTED_LOCAL.name)
                    statement.bindText(2, AppleGatewayCommandResultState.REJECTED.name)
                    statement.bindText(3, providerInstanceId)
                    statement.bindLong(4, reclaimBeforeMillis)
                    if (statement.step()) statement.readCommand() else null
                }
        if (command != null) {
            connection
                .prepare(
                    """
                    INSERT INTO command_claim(
                        caller_id, client_message_id, provider_instance_id, claimed_at_millis
                    ) VALUES(?, ?, ?, ?)
                    ON CONFLICT(caller_id, client_message_id) DO UPDATE SET
                        provider_instance_id=excluded.provider_instance_id,
                        claimed_at_millis=excluded.claimed_at_millis
                    WHERE command_claim.provider_instance_id != excluded.provider_instance_id
                       OR command_claim.claimed_at_millis <= ?
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, command.callerId)
                    statement.bindText(2, command.clientMessageId.uppercase())
                    statement.bindText(3, providerInstanceId)
                    statement.bindLong(4, claimedAtMillis)
                    statement.bindLong(5, reclaimBeforeMillis)
                    statement.step()
                }
            if (connection.changedRows() != 1L) null else command
        } else {
            null
        }
    }

    suspend fun releaseClaim(callerId: String, clientMessageId: String, providerInstanceId: String): Boolean =
        transaction { connection ->
            connection
                .prepare(
                    """
                    DELETE FROM command_claim
                    WHERE caller_id=? AND client_message_id=? AND provider_instance_id=?
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, callerId)
                    statement.bindText(2, clientMessageId.uppercase())
                    statement.bindText(3, providerInstanceId)
                    statement.step()
                }
            connection.changedRows() == 1L
        }

    suspend fun appendResult(result: AppleGatewayCommandResult) = transaction { connection ->
        connection.insertResult(result)
    }

    suspend fun appendNextResult(
        callerId: String,
        clientMessageId: String,
        state: AppleGatewayCommandResultState,
        packetId: Int?,
        reason: AppleGatewayRejectionReason?,
        updatedAtMillis: Long,
    ): AppleGatewayCommandResult = transaction { connection ->
        val canonicalClientMessageId = clientMessageId.uppercase()
        val nextSequence =
            connection
                .prepare(
                    """
                    SELECT COALESCE(MAX(result_seq), 0) + 1 FROM command_result
                    WHERE caller_id=? AND client_message_id=?
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, callerId)
                    statement.bindText(2, canonicalClientMessageId)
                    check(statement.step())
                    statement.getLong(0)
                }
        AppleGatewayCommandResult(
            callerId = callerId,
            clientMessageId = canonicalClientMessageId,
            resultSequence = nextSequence,
            state = state,
            packetId = packetId,
            reason = reason,
            updatedAtMillis = updatedAtMillis,
        )
            .also { result -> connection.insertResult(result) }
    }

    suspend fun readResults(callerId: String, clientMessageId: String): List<AppleGatewayCommandResult> =
        mutex.withLock {
            withInitializedConnection { connection ->
                connection
                    .prepare(
                        """
                        SELECT caller_id, client_message_id, result_seq, state, packet_id, reason, updated_at_millis
                        FROM command_result WHERE caller_id=? AND client_message_id=? ORDER BY result_seq
                        """
                            .trimIndent(),
                    )
                    .use { statement ->
                        statement.bindText(1, callerId)
                        statement.bindText(2, clientMessageId.uppercase())
                        buildList { while (statement.step()) add(statement.readResult()) }
                    }
            }
        }

    /** Atomically rejects nonce replay and removes only expired nonce records. */
    suspend fun reserveNonce(command: AppleGatewayCommand, nowMillis: Long): Boolean = reserveNonceForProcessing(
        command = command,
        requestFingerprint = AppleGatewayCommandCodec.requestFingerprint(command),
        nowMillis = nowMillis,
    ) == AppleGatewayNonceReservation.RESERVED

    /**
     * Reserves a nonce while allowing the exact same semantic command to resume after a provider crash. Reusing the
     * nonce for another client ID or fingerprint remains a replay rejection.
     */
    suspend fun reserveNonceForProcessing(
        command: AppleGatewayCommand,
        requestFingerprint: String,
        nowMillis: Long,
    ): AppleGatewayNonceReservation = transaction { connection ->
        connection.prepare("DELETE FROM used_nonce WHERE expires_at_millis <= ?").use { statement ->
            statement.bindLong(1, nowMillis)
            statement.step()
        }
        connection
            .prepare(
                """
                INSERT OR IGNORE INTO used_nonce(
                    caller_id, key_version, nonce, client_message_id, request_fingerprint, expires_at_millis
                ) VALUES(?, ?, ?, ?, ?, ?)
                """
                    .trimIndent(),
            )
            .use { statement ->
                statement.bindText(1, command.callerId)
                statement.bindInt(2, command.keyVersion)
                statement.bindBlob(3, command.nonce.toByteArray())
                statement.bindText(4, command.clientMessageId.uppercase())
                statement.bindText(5, requestFingerprint)
                statement.bindLong(6, command.expiresAtMillis)
                statement.step()
            }
        if (connection.changedRows() == 1L) {
            AppleGatewayNonceReservation.RESERVED
        } else {
            connection
                .prepare(
                    """
                    SELECT client_message_id, request_fingerprint FROM used_nonce
                    WHERE caller_id=? AND key_version=? AND nonce=?
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, command.callerId)
                    statement.bindInt(2, command.keyVersion)
                    statement.bindBlob(3, command.nonce.toByteArray())
                    check(statement.step()) { "Nonce reservation disappeared during its transaction" }
                    if (
                        statement.getText(0) == command.clientMessageId.uppercase() &&
                        statement.getText(1) == requestFingerprint
                    ) {
                        AppleGatewayNonceReservation.SAME_COMMAND
                    } else {
                        AppleGatewayNonceReservation.REPLAY
                    }
                }
        }
    }

    suspend fun appendOverlayIngress(ingress: AppleGatewayOverlayIngress): Boolean = transaction { connection ->
        require(ingress.historyEpoch.isNotBlank())
        require(ingress.changeSequence > 0)
        require(AppleGatewayOverlayIngressPolicy.accepts(ingress.portNumber, ingress.rawEnvelope))
        val inserted = connection.insertOverlayIngress(ingress)
        connection.advanceOverlayHighWater(ingress.historyEpoch, ingress.changeSequence)
        connection.trimOverlayIngress()
        inserted
    }

    /** Allocates the next epoch-scoped overlay sequence and inserts the envelope in the same durable transaction. */
    suspend fun appendNextOverlayIngress(
        historyEpoch: String,
        payload: AppleGatewayOverlayIngressPayload,
    ): AppleGatewayOverlayIngress = transaction { connection ->
        require(historyEpoch.isNotBlank())
        require(AppleGatewayOverlayIngressPolicy.accepts(payload.portNumber, payload.rawEnvelope))
        val currentHighWater = connection.readOverlayHighWater(historyEpoch)
        check(currentHighWater < Long.MAX_VALUE) { "Apple Gateway overlay sequence exhausted for epoch $historyEpoch" }
        val nextSequence = currentHighWater + 1
        AppleGatewayOverlayIngress(
            historyEpoch = historyEpoch,
            changeSequence = nextSequence,
            sourceChannelId = payload.sourceChannelId,
            sourceMessageId = payload.sourceMessageId,
            sourceNodeId = payload.sourceNodeId,
            packetId = payload.packetId,
            portNumber = payload.portNumber,
            rawEnvelope = payload.rawEnvelope,
            receivedAtMillis = payload.receivedAtMillis,
        )
            .also { ingress ->
                check(connection.insertOverlayIngress(ingress))
                connection.advanceOverlayHighWater(historyEpoch, nextSequence)
                connection.trimOverlayIngress()
            }
    }

    suspend fun readOverlayIngress(historyEpoch: String, after: Long, limit: Int): List<AppleGatewayOverlayIngress> =
        mutex.withLock {
            require(after >= 0)
            require(limit in 1..AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS)
            withInitializedConnection { connection ->
                connection
                    .prepare(
                        """
                        SELECT history_epoch, change_seq, source_channel_id, source_message_id, source_node_id,
                               packet_id, port_number, raw_envelope, received_at_millis
                        FROM overlay_ingress
                        WHERE history_epoch=? AND change_seq>?
                        ORDER BY change_seq LIMIT ?
                        """
                            .trimIndent(),
                    )
                    .use { statement ->
                        statement.bindText(1, historyEpoch)
                        statement.bindLong(2, after)
                        statement.bindInt(3, limit)
                        buildList { while (statement.step()) add(statement.readIngress()) }
                    }
            }
        }

    suspend fun readOverlayHighWater(historyEpoch: String): Long = mutex.withLock {
        require(historyEpoch.isNotBlank())
        withInitializedConnection { connection -> connection.readOverlayHighWater(historyEpoch) }
    }

    suspend fun appendNativeMessageChange(change: AppleGatewayNativeMessageChange): Boolean =
        transaction { connection ->
            require(change.historyEpoch.isNotBlank())
            require(change.changeSequence > 0)
            require(change.sourceChannelId.isNotBlank())
            require(change.sourceMessageId.isNotBlank())
            require(change.fromNodeId.isNotBlank())
            require(NtsocialGatewayNativeText.isValid(change.text))
            connection
                .prepare(
                    """
                    INSERT OR IGNORE INTO native_message_change(
                        history_epoch, change_seq, source_channel_id, source_message_id, from_node_id,
                        packet_id, text, received_at_millis, origin_client_message_id
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, change.historyEpoch)
                    statement.bindLong(2, change.changeSequence)
                    statement.bindText(3, change.sourceChannelId)
                    statement.bindText(4, change.sourceMessageId)
                    statement.bindText(5, change.fromNodeId)
                    statement.bindLong(6, change.packetId.toLong())
                    statement.bindText(7, change.text)
                    statement.bindLong(8, change.receivedAtMillis)
                    statement.bindNullableText(9, change.originClientMessageId)
                    statement.step()
                }
            connection.changedRows() == 1L
        }

    suspend fun readNativeMessageChanges(
        historyEpoch: String,
        after: Long,
        limit: Int = AppleGatewayContract.DEFAULT_NATIVE_MESSAGE_CHANGE_PAGE_SIZE,
    ): List<AppleGatewayNativeMessageChange> = mutex.withLock {
        require(historyEpoch.isNotBlank())
        require(after >= 0)
        require(limit in 1..AppleGatewayContract.MAX_NATIVE_MESSAGE_CHANGE_PAGE_SIZE)
        withInitializedConnection { connection ->
            connection
                .prepare(
                    """
                        SELECT history_epoch, change_seq, source_channel_id, source_message_id, from_node_id,
                               packet_id, text, received_at_millis, origin_client_message_id
                        FROM native_message_change
                        WHERE history_epoch=? AND change_seq>?
                        ORDER BY change_seq LIMIT ?
                        """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, historyEpoch)
                    statement.bindLong(2, after)
                    statement.bindInt(3, limit)
                    buildList { while (statement.step()) add(statement.readNativeMessageChange()) }
                }
        }
    }

    suspend fun readNativeMessageHighWater(historyEpoch: String): Long = mutex.withLock {
        require(historyEpoch.isNotBlank())
        withInitializedConnection { connection ->
            connection
                .prepare("SELECT COALESCE(MAX(change_seq), 0) FROM native_message_change WHERE history_epoch=?")
                .use { statement ->
                    statement.bindText(1, historyEpoch)
                    check(statement.step())
                    statement.getLong(0)
                }
        }
    }

    suspend fun commitCursor(
        callerId: String,
        streamName: String,
        historyEpoch: String,
        committedSequence: Long,
        updatedAtMillis: Long,
    ) = transaction { connection ->
        require(committedSequence >= 0)
        connection
            .prepare(
                """
                INSERT INTO consumer_cursor(caller_id, stream_name, history_epoch, committed_seq, updated_at_millis)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(caller_id, stream_name) DO UPDATE SET
                    history_epoch=excluded.history_epoch,
                    committed_seq=excluded.committed_seq,
                    updated_at_millis=excluded.updated_at_millis
                WHERE consumer_cursor.history_epoch != excluded.history_epoch
                   OR consumer_cursor.committed_seq <= excluded.committed_seq
                """
                    .trimIndent(),
            )
            .use { statement ->
                statement.bindText(1, callerId)
                statement.bindText(2, streamName)
                statement.bindText(3, historyEpoch)
                statement.bindLong(4, committedSequence)
                statement.bindLong(5, updatedAtMillis)
                statement.step()
            }
    }

    suspend fun resetSharedGateway() = transaction { connection ->
        listOf(
            "channel_projection",
            "gateway_meta",
            "gateway_caller_projection",
            "command_result",
            "command_claim",
            "command_inbox",
            "overlay_ingress",
            "overlay_epoch_state",
            "native_message_change",
            "consumer_cursor",
            "used_nonce",
        )
            .forEach { table -> connection.execute("DELETE FROM $table") }
    }

    /** Removes one caller's shared projection and mailbox; MeshLink-private route and ledger state are not touched. */
    suspend fun resetCallerProjection(callerId: String) = transaction { connection ->
        // Version 1 has exactly one authorized parent caller, so every projected route belongs to this caller.
        connection.execute("DELETE FROM channel_projection")
        connection.prepare("DELETE FROM gateway_caller_projection WHERE caller_id=?").use { statement ->
            statement.bindText(1, callerId)
            statement.step()
        }
        connection.prepare("DELETE FROM command_inbox WHERE caller_id=?").use { statement ->
            statement.bindText(1, callerId)
            statement.step()
        }
        connection.prepare("DELETE FROM consumer_cursor WHERE caller_id=?").use { statement ->
            statement.bindText(1, callerId)
            statement.step()
        }
        connection.prepare("DELETE FROM used_nonce WHERE caller_id=?").use { statement ->
            statement.bindText(1, callerId)
            statement.step()
        }
    }

    private suspend fun <T> transaction(block: (SQLiteConnection) -> T): T = mutex.withLock {
        withInitializedConnection { connection ->
            connection.execute("BEGIN IMMEDIATE")
            try {
                block(connection).also { connection.execute("COMMIT") }
            } catch (error: Throwable) {
                runCatching { connection.execute("ROLLBACK") }
                throw error
            }
        }
    }

    private fun <T> withInitializedConnection(block: (SQLiteConnection) -> T): T = withConnection { connection ->
        migrate(connection)
        block(connection)
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T = driver.open(
        databasePath,
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
    ).use { connection ->
        connection.execute("PRAGMA busy_timeout=5000")
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA synchronous=FULL")
        block(connection)
    }

    private fun migrate(connection: SQLiteConnection) {
        val version = connection.queryLong("PRAGMA user_version")
        check(version <= AppleGatewayContract.SCHEMA_VERSION) {
            "Apple Gateway schema $version is newer than supported ${AppleGatewayContract.SCHEMA_VERSION}"
        }
        if (version == 0L) {
            connection.execute("BEGIN IMMEDIATE")
            try {
                AppleGatewaySchema.createVersion1.forEach { statement -> connection.execute(statement) }
                connection.execute("PRAGMA user_version=${AppleGatewayContract.SCHEMA_VERSION}")
                connection.execute("COMMIT")
            } catch (error: Throwable) {
                runCatching { connection.execute("ROLLBACK") }
                throw error
            }
        }
        if (!connection.tableExists("overlay_epoch_state")) {
            // Early schema-v1 builds predated durable overlay allocation state. Keep user_version at 1 for the shared
            // Swift/Kotlin contract and reconstruct the best high-water still available from retained rows.
            connection.execute("BEGIN IMMEDIATE")
            try {
                connection.execute(AppleGatewaySchema.createOverlayEpochState)
                connection.execute(
                    """
                    INSERT INTO overlay_epoch_state(history_epoch, high_water)
                    SELECT history_epoch, MAX(change_seq) FROM overlay_ingress GROUP BY history_epoch
                    ON CONFLICT(history_epoch) DO UPDATE SET high_water=
                        CASE
                            WHEN excluded.high_water > overlay_epoch_state.high_water THEN excluded.high_water
                            ELSE overlay_epoch_state.high_water
                        END
                    """
                        .trimIndent(),
                )
                connection.execute("COMMIT")
            } catch (error: Throwable) {
                runCatching { connection.execute("ROLLBACK") }
                throw error
            }
        }
    }

    private fun SQLiteConnection.insertChannel(channel: AppleGatewayChannelProjection) {
        prepare(
            """
                INSERT INTO channel_projection(
                    radio_generation, slot_index, source_channel_id, display_name, role, security_class,
                    capabilities, route_token, route_expires_at_millis
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                .trimIndent(),
        )
            .use { statement ->
                statement.bindText(1, channel.radioGeneration)
                statement.bindInt(2, channel.slotIndex)
                statement.bindText(3, channel.sourceChannelId)
                statement.bindText(4, channel.displayName)
                statement.bindText(5, channel.role)
                statement.bindText(6, channel.securityClass)
                statement.bindText(7, channel.capabilities.map { it.name }.sorted().joinToString(","))
                statement.bindText(8, channel.routeToken)
                statement.bindLong(9, channel.routeExpiresAtMillis)
                statement.step()
            }
    }

    private fun SQLiteConnection.upsertCallerProjection(caller: AppleGatewayCallerProjection) {
        prepare(
            """
                INSERT INTO gateway_caller_projection(
                    caller_id, active_key_version, revoked, last_seen_at_millis
                ) VALUES(?, ?, ?, ?)
                ON CONFLICT(caller_id) DO UPDATE SET
                    active_key_version=excluded.active_key_version,
                    revoked=excluded.revoked,
                    last_seen_at_millis=excluded.last_seen_at_millis
                """
                .trimIndent(),
        )
            .use { statement ->
                statement.bindText(1, caller.callerId)
                statement.bindInt(2, caller.activeKeyVersion)
                statement.bindBoolean(3, caller.revoked)
                statement.bindLong(4, caller.lastSeenAtMillis)
                statement.step()
            }
    }

    private fun SQLiteConnection.insertResult(result: AppleGatewayCommandResult) {
        prepare(
            """
                INSERT INTO command_result(
                    caller_id, client_message_id, result_seq, state, packet_id, reason, updated_at_millis
                ) VALUES(?, ?, ?, ?, ?, ?, ?)
                """
                .trimIndent(),
        )
            .use { statement ->
                statement.bindText(1, result.callerId)
                statement.bindText(2, result.clientMessageId.uppercase())
                statement.bindLong(3, result.resultSequence)
                statement.bindText(4, result.state.name)
                statement.bindNullableInt(5, result.packetId)
                statement.bindNullableText(6, result.reason?.wireValue)
                statement.bindLong(7, result.updatedAtMillis)
                statement.step()
            }
    }

    private fun SQLiteConnection.insertOverlayIngress(ingress: AppleGatewayOverlayIngress): Boolean {
        prepare(
            """
                INSERT OR IGNORE INTO overlay_ingress(
                    history_epoch, change_seq, source_channel_id, source_message_id, source_node_id,
                    packet_id, port_number, raw_envelope, received_at_millis
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                .trimIndent(),
        )
            .use { statement ->
                statement.bindText(1, ingress.historyEpoch)
                statement.bindLong(2, ingress.changeSequence)
                statement.bindText(3, ingress.sourceChannelId)
                statement.bindText(4, ingress.sourceMessageId)
                statement.bindText(5, ingress.sourceNodeId)
                statement.bindLong(6, ingress.packetId.toLong())
                statement.bindInt(7, ingress.portNumber)
                statement.bindBlob(8, ingress.rawEnvelope.toByteArray())
                statement.bindLong(9, ingress.receivedAtMillis)
                statement.step()
            }
        return changedRows() == 1L
    }

    private fun SQLiteConnection.advanceOverlayHighWater(historyEpoch: String, highWater: Long) {
        prepare(
            """
                INSERT INTO overlay_epoch_state(history_epoch, high_water) VALUES(?, ?)
                ON CONFLICT(history_epoch) DO UPDATE SET high_water=
                    CASE
                        WHEN excluded.high_water > overlay_epoch_state.high_water THEN excluded.high_water
                        ELSE overlay_epoch_state.high_water
                    END
                """
                .trimIndent(),
        )
            .use { statement ->
                statement.bindText(1, historyEpoch)
                statement.bindLong(2, highWater)
                statement.step()
            }
    }

    private fun SQLiteConnection.readOverlayHighWater(historyEpoch: String): Long =
        prepare("SELECT high_water FROM overlay_epoch_state WHERE history_epoch=?").use { statement ->
            statement.bindText(1, historyEpoch)
            if (statement.step()) statement.getLong(0) else 0
        }

    private fun SQLiteConnection.trimOverlayIngress() {
        execute(
            """
            DELETE FROM overlay_ingress WHERE rowid IN (
                SELECT rowid FROM overlay_ingress
                ORDER BY received_at_millis DESC, history_epoch DESC, change_seq DESC
                LIMIT -1 OFFSET ${AppleGatewayContract.MAX_OVERLAY_INGRESS_RECORDS}
            )
            """
                .trimIndent(),
        )
    }

    private fun SQLiteStatement.bindCommand(command: AppleGatewayCommand, receivedAtMillis: Long) {
        bindText(1, command.callerId)
        bindText(2, command.clientMessageId.uppercase())
        bindInt(3, command.schemaVersion)
        bindText(4, command.requestId)
        bindText(5, command.sourceChannelId)
        bindText(6, command.routeToken)
        bindText(7, command.radioGeneration)
        bindLong(8, command.issuedAtMillis)
        bindLong(9, command.expiresAtMillis)
        bindInt(10, command.keyVersion)
        bindBlob(11, command.nonce.toByteArray())
        when (val body = command.body) {
            is AppleGatewayCommandBody.NtsocialEnvelope -> {
                bindText(12, "SEND_NTSOCIAL_ENVELOPE_TO_ROUTE")
                bindBlob(13, body.rawEnvelope.toByteArray())
                bindNullableText(14, body.destination)
                bindInt(15, body.hopLimit)
                bindBoolean(16, body.wantAck)
            }

            is AppleGatewayCommandBody.NativeBroadcastText -> {
                bindText(12, "SEND_CHANNEL_TEXT")
                bindBlob(13, body.text.encodeToByteArray())
                bindNull(14)
                bindInt(15, 0)
                bindBoolean(16, true)
            }
        }
        bindBlob(17, command.authenticationTag.toByteArray())
        bindLong(18, receivedAtMillis)
    }

    private fun SQLiteStatement.readCommand(): AppleGatewayCommand {
        val body =
            when (val commandType = getText(11)) {
                "SEND_NTSOCIAL_ENVELOPE_TO_ROUTE" ->
                    AppleGatewayCommandBody.NtsocialEnvelope(
                        rawEnvelope = getBlob(12).toByteString(),
                        destination = getNullableText(13),
                        hopLimit = getInt(14),
                        wantAck = getBoolean(15),
                    )

                "SEND_CHANNEL_TEXT" ->
                    AppleGatewayCommandBody.NativeBroadcastText(
                        getBlob(12).decodeToString(throwOnInvalidSequence = true),
                    )

                else -> error("Unsupported Apple Gateway command type: $commandType")
            }
        return AppleGatewayCommand(
            schemaVersion = getInt(0),
            callerId = getText(1),
            requestId = getText(2),
            clientMessageId = getText(3),
            sourceChannelId = getText(4),
            routeToken = getText(5),
            radioGeneration = getText(6),
            issuedAtMillis = getLong(7),
            expiresAtMillis = getLong(8),
            keyVersion = getInt(9),
            nonce = getBlob(10).toByteString(),
            body = body,
            authenticationTag = getBlob(16).toByteString(),
        )
    }

    private fun SQLiteStatement.readStatus() = AppleGatewayStatus(
        schemaVersion = getInt(0),
        providerInstanceId = getText(1),
        readiness = AppleGatewayReadiness.valueOf(getText(2)),
        radioGeneration = getText(3),
        historyEpoch = getNullableText(4),
        overlayHighWater = getLong(5),
        nativeTextHighWater = getLong(6),
        activeKeyVersion = getInt(7),
        updatedAtMillis = getLong(8),
    )

    private fun SQLiteStatement.readChannel() = AppleGatewayChannelProjection(
        sourceChannelId = getText(0),
        slotIndex = getInt(1),
        displayName = getText(2),
        role = getText(3),
        securityClass = getText(4),
        capabilities =
        getText(5)
            .split(',')
            .filter(String::isNotBlank)
            .mapTo(mutableSetOf(), AppleGatewayRouteCapability::valueOf),
        routeToken = getText(6),
        routeExpiresAtMillis = getLong(7),
        radioGeneration = getText(8),
    )

    private fun SQLiteStatement.readResult() = AppleGatewayCommandResult(
        callerId = getText(0),
        clientMessageId = getText(1),
        resultSequence = getLong(2),
        state = AppleGatewayCommandResultState.valueOf(getText(3)),
        packetId = if (isNull(4)) null else getInt(4),
        reason =
        getNullableText(5)?.let { reason ->
            AppleGatewayRejectionReason.entries.single { it.wireValue == reason }
        },
        updatedAtMillis = getLong(6),
    )

    private fun SQLiteStatement.readIngress() = AppleGatewayOverlayIngress(
        historyEpoch = getText(0),
        changeSequence = getLong(1),
        sourceChannelId = getText(2),
        sourceMessageId = getText(3),
        sourceNodeId = getText(4),
        packetId = getLong(5).toUInt(),
        portNumber = getInt(6),
        rawEnvelope = getBlob(7).toByteString(),
        receivedAtMillis = getLong(8),
    )

    private fun SQLiteStatement.readNativeMessageChange() = AppleGatewayNativeMessageChange(
        historyEpoch = getText(0),
        changeSequence = getLong(1),
        sourceChannelId = getText(2),
        sourceMessageId = getText(3),
        fromNodeId = getText(4),
        packetId = getLong(5).toUInt(),
        text = getText(6),
        receivedAtMillis = getLong(7),
        originClientMessageId = getNullableText(8),
    )

    private fun SQLiteConnection.changedRows(): Long = queryLong("SELECT changes()")

    private fun SQLiteConnection.tableExists(tableName: String): Boolean =
        prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { statement ->
            statement.bindText(1, tableName)
            statement.step()
        }

    private fun SQLiteConnection.queryLong(sql: String): Long = prepare(sql).use { statement ->
        check(statement.step()) { "SQLite query returned no row: $sql" }
        statement.getLong(0)
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use(SQLiteStatement::step)
    }

    private fun SQLiteStatement.bindNullableText(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindText(index, value)
    }

    private fun SQLiteStatement.bindNullableInt(index: Int, value: Int?) {
        if (value == null) bindNull(index) else bindInt(index, value)
    }

    private fun SQLiteStatement.getNullableText(index: Int): String? = if (isNull(index)) null else getText(index)
}
