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
@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.ntsocial.meshlink.core.gateway.apple

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AppleGatewayLedger {
    /** Reads authoritative private state without creating or mutating a reservation. */
    suspend fun lookup(callerId: String, canonicalClientMessageId: String): AppleGatewayLedgerRecord?

    suspend fun reserve(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
    ): AppleGatewayLedgerReservation

    suspend fun markAccepted(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
        packetId: Int,
    )
}

/** MeshLink-private, restart-stable idempotency ledger. This file must never be placed in the App Group. */
class AppleGatewayPrivateLedger(private val databasePath: String) : AppleGatewayLedger {
    private val driver = BundledSQLiteDriver()
    private val mutex = Mutex()

    override suspend fun reserve(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
    ): AppleGatewayLedgerReservation = transaction { connection ->
        val existing = connection.readRecord(callerId, canonicalClientMessageId)
        if (existing != null) {
            AppleGatewayIdempotencyPolicy.reserve(
                callerId = callerId,
                canonicalClientMessageId = canonicalClientMessageId,
                requestFingerprint = requestFingerprint,
                existing = existing,
            )
        } else {
            val packetId = AppleGatewayIdempotencyPolicy.deterministicPacketId(callerId, canonicalClientMessageId)
            val insertionSequence = connection.nextInsertionSequence(callerId)
            connection
                .prepare(
                    """
                    INSERT INTO private_ledger(
                        caller_id, client_message_id, request_fingerprint, state, packet_id, insertion_seq
                    ) VALUES(?, ?, ?, ?, ?, ?)
                    """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, callerId)
                    statement.bindText(2, canonicalClientMessageId)
                    statement.bindText(3, requestFingerprint)
                    statement.bindText(4, AppleGatewayLedgerState.PENDING.name)
                    statement.bindInt(5, packetId)
                    statement.bindLong(6, insertionSequence)
                    statement.step()
                }
            connection
                .prepare(
                    """
                DELETE FROM private_ledger
                WHERE caller_id=? AND client_message_id IN (
                    SELECT client_message_id FROM private_ledger WHERE caller_id=?
                    ORDER BY insertion_seq DESC, client_message_id DESC
                    LIMIT -1 OFFSET ${AppleGatewayContract.MAX_LEDGER_RECORDS_PER_CALLER}
                )
                """
                        .trimIndent(),
                )
                .use { statement ->
                    statement.bindText(1, callerId)
                    statement.bindText(2, callerId)
                    statement.step()
                }
            AppleGatewayLedgerReservation.Pending(packetId)
        }
    }

    override suspend fun markAccepted(
        callerId: String,
        canonicalClientMessageId: String,
        requestFingerprint: String,
        packetId: Int,
    ) = transaction { connection ->
        connection
            .prepare(
                """
                UPDATE private_ledger SET state=?
                WHERE caller_id=? AND client_message_id=? AND request_fingerprint=? AND packet_id=?
                """
                    .trimIndent(),
            )
            .use { statement ->
                statement.bindText(1, AppleGatewayLedgerState.ACCEPTED.name)
                statement.bindText(2, callerId)
                statement.bindText(3, canonicalClientMessageId)
                statement.bindText(4, requestFingerprint)
                statement.bindInt(5, packetId)
                statement.step()
            }
        check(connection.changedRows() == 1L) { "Gateway acceptance does not match its durable reservation" }
    }

    override suspend fun lookup(callerId: String, canonicalClientMessageId: String): AppleGatewayLedgerRecord? =
        mutex.withLock {
            withConnection { connection ->
                initialize(connection)
                connection.readRecord(callerId, canonicalClientMessageId)
            }
        }

    private suspend fun <T> transaction(block: (SQLiteConnection) -> T): T = mutex.withLock {
        withConnection { connection ->
            initialize(connection)
            connection.execute("BEGIN IMMEDIATE")
            try {
                block(connection).also { connection.execute("COMMIT") }
            } catch (error: Throwable) {
                runCatching { connection.execute("ROLLBACK") }
                throw error
            }
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T = driver.open(
        databasePath,
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
    ).use { connection ->
        connection.execute("PRAGMA busy_timeout=5000")
        connection.execute("PRAGMA synchronous=FULL")
        block(connection)
    }

    private fun initialize(connection: SQLiteConnection) {
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS private_ledger(
                caller_id TEXT NOT NULL,
                client_message_id TEXT NOT NULL,
                request_fingerprint TEXT NOT NULL,
                state TEXT NOT NULL,
                packet_id INTEGER NOT NULL,
                insertion_seq INTEGER NOT NULL,
                PRIMARY KEY(caller_id, client_message_id)
            )
            """
                .trimIndent(),
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS private_ledger_insertion_idx ON private_ledger(caller_id, insertion_seq)",
        )
    }

    private fun SQLiteConnection.readRecord(callerId: String, clientMessageId: String): AppleGatewayLedgerRecord? =
        prepare(
            """
                SELECT caller_id, client_message_id, request_fingerprint, state, packet_id, insertion_seq
                FROM private_ledger WHERE caller_id=? AND client_message_id=?
                """
                .trimIndent(),
        )
            .use { statement ->
                statement.bindText(1, callerId)
                statement.bindText(2, clientMessageId)
                if (!statement.step()) {
                    null
                } else {
                    AppleGatewayLedgerRecord(
                        callerId = statement.getText(0),
                        clientMessageId = statement.getText(1),
                        requestFingerprint = statement.getText(2),
                        state = AppleGatewayLedgerState.valueOf(statement.getText(3)),
                        packetId = statement.getInt(4),
                        insertionSequence = statement.getLong(5),
                    )
                }
            }

    private fun SQLiteConnection.changedRows(): Long = prepare("SELECT changes()").use { statement ->
        check(statement.step())
        statement.getLong(0)
    }

    private fun SQLiteConnection.nextInsertionSequence(callerId: String): Long =
        prepare("SELECT COALESCE(MAX(insertion_seq), 0) + 1 FROM private_ledger WHERE caller_id=?").use { statement ->
            statement.bindText(1, callerId)
            check(statement.step())
            statement.getLong(0)
        }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use(SQLiteStatement::step)
    }
}
