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

/** Explicit shared-container schema consumed by both Kotlin and the separately versioned Swift parent adapter. */
object AppleGatewaySchema {
    const val FILE_NAME = "gateway-v1.sqlite"

    /**
     * Additive schema-v1 state that keeps an epoch's allocated overlay sequence monotonic even after its retained rows
     * have been evicted. Keep this statement synchronized with the separately versioned Swift mailbox schema.
     */
    val createOverlayEpochState =
        """
        CREATE TABLE IF NOT EXISTS overlay_epoch_state (
            history_epoch TEXT NOT NULL PRIMARY KEY,
            high_water INTEGER NOT NULL CHECK (high_water >= 0)
        )
        """
            .trimIndent()

    val createVersion1: List<String> =
        listOf(
            """
            CREATE TABLE IF NOT EXISTS gateway_meta (
                singleton_id INTEGER NOT NULL PRIMARY KEY CHECK (singleton_id = 1),
                schema_version INTEGER NOT NULL,
                provider_instance_id TEXT NOT NULL,
                readiness TEXT NOT NULL,
                radio_generation TEXT NOT NULL,
                history_epoch TEXT,
                overlay_high_water INTEGER NOT NULL,
                native_text_high_water INTEGER NOT NULL,
                active_key_version INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL
            )
            """
                .trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS gateway_caller_projection (
                caller_id TEXT NOT NULL PRIMARY KEY,
                active_key_version INTEGER NOT NULL,
                revoked INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0, 1)),
                last_seen_at_millis INTEGER NOT NULL
            )
            """
                .trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS channel_projection (
                radio_generation TEXT NOT NULL,
                slot_index INTEGER NOT NULL,
                source_channel_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                role TEXT NOT NULL,
                security_class TEXT NOT NULL,
                capabilities TEXT NOT NULL,
                route_token TEXT NOT NULL,
                route_expires_at_millis INTEGER NOT NULL,
                PRIMARY KEY (radio_generation, slot_index)
            )
            """
                .trimIndent(),
            "CREATE INDEX IF NOT EXISTS channel_projection_source_idx ON channel_projection(source_channel_id)",
            """
            CREATE TABLE IF NOT EXISTS command_inbox (
                caller_id TEXT NOT NULL,
                client_message_id TEXT NOT NULL,
                schema_version INTEGER NOT NULL,
                request_id TEXT NOT NULL,
                source_channel_id TEXT NOT NULL,
                route_token TEXT NOT NULL,
                radio_generation TEXT NOT NULL,
                issued_at_millis INTEGER NOT NULL,
                expires_at_millis INTEGER NOT NULL,
                key_version INTEGER NOT NULL,
                nonce BLOB NOT NULL,
                command_type TEXT NOT NULL,
                body_payload BLOB NOT NULL,
                destination TEXT,
                hop_limit INTEGER NOT NULL,
                want_ack INTEGER NOT NULL CHECK (want_ack IN (0, 1)),
                authentication_tag BLOB NOT NULL,
                received_at_millis INTEGER NOT NULL,
                PRIMARY KEY (caller_id, client_message_id)
            )
            """
                .trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS command_claim (
                caller_id TEXT NOT NULL,
                client_message_id TEXT NOT NULL,
                provider_instance_id TEXT NOT NULL,
                claimed_at_millis INTEGER NOT NULL,
                PRIMARY KEY (caller_id, client_message_id),
                FOREIGN KEY (caller_id, client_message_id)
                    REFERENCES command_inbox(caller_id, client_message_id) ON DELETE CASCADE
            )
            """
                .trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS command_result (
                caller_id TEXT NOT NULL,
                client_message_id TEXT NOT NULL,
                result_seq INTEGER NOT NULL,
                state TEXT NOT NULL,
                packet_id INTEGER,
                reason TEXT,
                updated_at_millis INTEGER NOT NULL,
                PRIMARY KEY (caller_id, client_message_id, result_seq),
                FOREIGN KEY (caller_id, client_message_id)
                    REFERENCES command_inbox(caller_id, client_message_id) ON DELETE CASCADE
            )
            """
                .trimIndent(),
            "CREATE INDEX IF NOT EXISTS command_result_updated_idx ON command_result(updated_at_millis)",
            """
            CREATE TABLE IF NOT EXISTS overlay_ingress (
                history_epoch TEXT NOT NULL,
                change_seq INTEGER NOT NULL,
                source_channel_id TEXT NOT NULL,
                source_message_id TEXT NOT NULL,
                source_node_id TEXT NOT NULL,
                packet_id INTEGER NOT NULL,
                port_number INTEGER NOT NULL,
                raw_envelope BLOB NOT NULL,
                received_at_millis INTEGER NOT NULL,
                PRIMARY KEY (history_epoch, change_seq)
            )
            """
                .trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS overlay_ingress_source_idx
            ON overlay_ingress(source_channel_id, source_message_id)
            """
                .trimIndent(),
            createOverlayEpochState,
            """
            CREATE TABLE IF NOT EXISTS native_message_change (
                history_epoch TEXT NOT NULL,
                change_seq INTEGER NOT NULL,
                source_channel_id TEXT NOT NULL,
                source_message_id TEXT NOT NULL,
                from_node_id TEXT NOT NULL,
                packet_id INTEGER NOT NULL,
                text TEXT NOT NULL,
                received_at_millis INTEGER NOT NULL,
                origin_client_message_id TEXT,
                PRIMARY KEY (history_epoch, change_seq)
            )
            """
                .trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS native_message_change_source_idx
            ON native_message_change(source_channel_id, source_message_id)
            """
                .trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS consumer_cursor (
                    caller_id TEXT NOT NULL,
                    stream_name TEXT NOT NULL,
                    history_epoch TEXT NOT NULL,
                    committed_seq INTEGER NOT NULL,
                    updated_at_millis INTEGER NOT NULL,
                    PRIMARY KEY (caller_id, stream_name)
                )
            """
                .trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS used_nonce (
                caller_id TEXT NOT NULL,
                key_version INTEGER NOT NULL,
                nonce BLOB NOT NULL,
                client_message_id TEXT NOT NULL,
                request_fingerprint TEXT NOT NULL,
                expires_at_millis INTEGER NOT NULL,
                PRIMARY KEY (caller_id, key_version, nonce)
            )
            """
                .trimIndent(),
            "CREATE INDEX IF NOT EXISTS used_nonce_expiry_idx ON used_nonce(expires_at_millis)",
        )
}
