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
package com.ntsocial.meshlink.core.gateway

import android.net.Uri
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayNativeText

/**
 * Versioned NTsocial MeshLink Gateway IPC contract.
 *
 * The Binder contract remains only for transitional compatibility. New integrations use the protected Provider for
 * snapshots, an explicit command broadcast for outbound packets, and metadata-only events to prompt re-queries.
 */
object NtsocialGatewayContract {
    private const val PREFIX = "com.ntsocial.meshlink"

    const val API_VERSION = 1
    const val API_VERSION_V2 = 2
    const val AUTHORITY_SUFFIX = ".gateway"

    const val PATH_VERSION = "v1"
    const val PATH_VERSION_V2 = "v2"
    const val PATH_STATUS = "status"
    const val PATH_ENVELOPES = "envelopes"
    const val PATH_NODES = "nodes"
    const val PATH_CHANNELS = "channels"
    const val PATH_MESSAGE_CHANGES = "message-changes"

    const val QUERY_AFTER = "after"
    const val QUERY_LIMIT = "limit"

    const val ACTION_COMMAND = "$PREFIX.gateway.COMMAND"
    const val ACTION_EVENT = "$PREFIX.gateway.EVENT"

    const val PERMISSION_ACCESS = "$PREFIX.permission.ACCESS_NTSOCIAL_GATEWAY"
    const val PERMISSION_CONTROL = "$PREFIX.permission.CONTROL_NTSOCIAL_GATEWAY"

    /** @deprecated New clients must use [ACTION_COMMAND], [ACTION_EVENT], and the versioned Provider endpoints. */
    @Deprecated("Use ContentProvider and command/event broadcasts instead")
    const val ACTION_BIND = "$PREFIX.gateway.BIND"

    /** @deprecated The AIDL compatibility adapter is not the Gateway API for new clients. */
    @Deprecated("Use ContentProvider and command/event broadcasts instead")
    const val PERMISSION_BIND = "$PREFIX.permission.BIND_NTSOCIAL_GATEWAY"

    const val METHOD_ISSUE_COMMAND_CAPABILITY = "issue_command_capability"

    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_COMMAND_TYPE = "command_type"
    const val EXTRA_PAYLOAD = "payload"
    const val EXTRA_CHANNEL_INDEX = "channel_index"
    const val EXTRA_SOURCE_CHANNEL_ID = "source_channel_id"
    const val EXTRA_ROUTE_TOKEN = "route_token"
    const val EXTRA_CLIENT_MESSAGE_ID = "client_message_id"
    const val CLIENT_MESSAGE_ID_HEX_LENGTH = 32
    const val EXTRA_TEXT = "text"
    const val EXTRA_TO = "to"
    const val EXTRA_HOP_LIMIT = "hop_limit"
    const val EXTRA_WANT_ACK = "want_ack"
    const val EXTRA_EVENT_TYPE = "event_type"
    const val EXTRA_PACKET_ID = "packet_id"
    const val EXTRA_REASON = "reason"
    const val EXTRA_URI = "uri"
    const val EXTRA_AUTHORIZATION_TOKEN = "authorization_token"
    const val EXTRA_AUTHORIZATION_EXPIRES_AT_MILLIS = "authorization_expires_at_millis"

    const val EVENT_ENVELOPE_AVAILABLE = "ENVELOPE_AVAILABLE"
    const val EVENT_COMMAND_ACCEPTED = "COMMAND_ACCEPTED"
    const val EVENT_COMMAND_REJECTED = "COMMAND_REJECTED"
    const val EVENT_STATUS_CHANGED = "STATUS_CHANGED"
    const val EVENT_CHANNEL_CATALOG_CHANGED = "CHANNEL_CATALOG_CHANGED"
    const val EVENT_MESSAGE_CHANGES_AVAILABLE = "MESSAGE_CHANGES_AVAILABLE"

    const val COMMAND_SEND_NTSOCIAL_ENVELOPE_TO_ROUTE = "SEND_NTSOCIAL_ENVELOPE_TO_ROUTE"
    const val COMMAND_SEND_CHANNEL_TEXT = "SEND_CHANNEL_TEXT"

    const val CAPABILITY_CHANNEL_CATALOG = 1L
    const val CAPABILITY_NATIVE_TEXT_HISTORY = 1L shl 1
    const val CAPABILITY_ROUTE_OVERLAY_SEND = 1L shl 2
    const val CAPABILITY_MESSAGE_CHANGE_EVENTS = 1L shl 3
    const val CAPABILITY_NATIVE_TEXT_SEND = 1L shl 4
    const val MAX_NATIVE_TEXT_SIZE_BYTES = NtsocialGatewayNativeText.MAX_UTF8_SIZE_BYTES

    /** SHA-256 prefix exported as 16 bytes / 32 uppercase hexadecimal characters. */
    const val SOURCE_MESSAGE_ID_HEX_LENGTH = 32

    const val COLUMN_API_VERSION = "api_version"
    const val COLUMN_CONNECTION_STATE = "connection_state"
    const val COLUMN_CACHED_ENVELOPE_COUNT = "cached_envelope_count"
    const val COLUMN_CACHED_ENVELOPE_LIMIT = "cached_envelope_limit"
    const val COLUMN_PRIVATE_APP_PORT_NUM = "private_app_port_num"
    const val COLUMN_LEGACY_RECEIVE_ONLY_PORT_NUM = "legacy_receive_only_port_num"
    const val COLUMN_MAX_ENVELOPE_SIZE_BYTES = "max_envelope_size_bytes"
    const val COLUMN_MAX_PAYLOAD_SIZE_BYTES = "max_payload_size_bytes"
    const val COLUMN_MAX_COMMAND_ENVELOPE_SIZE_BYTES = "max_command_envelope_size_bytes"
    const val COLUMN_DEFAULT_CHANNEL_READY = "default_channel_ready"
    const val COLUMN_DEFAULT_CHANNEL_INDEX = "default_channel_index"
    const val COLUMN_PROVISIONING_STATE = "provisioning_state"
    const val COLUMN_PROVISIONING_CHANNEL_CHANGE = "provisioning_channel_change"
    const val COLUMN_PROVISIONING_LORA_APPLIED = "provisioning_lora_applied"
    const val COLUMN_LOCAL_NODE_ID = "local_node_id"
    const val COLUMN_GATEWAY_BATTERY_PERCENT = "gateway_battery_percent"
    const val COLUMN_GATEWAY_VOLTAGE = "gateway_voltage"
    const val COLUMN_GATEWAY_CHANNEL_UTILIZATION = "gateway_channel_utilization"
    const val COLUMN_GATEWAY_AIR_UTIL_TX = "gateway_air_util_tx"
    const val COLUMN_CAPABILITIES = "capabilities"
    const val COLUMN_BEARER = "bearer"
    const val COLUMN_RADIO_GENERATION = "radio_generation"
    const val COLUMN_HISTORY_EPOCH = "history_epoch"
    const val COLUMN_MESSAGE_CHANGE_SEQ = "message_change_seq"
    const val COLUMN_NATIVE_HISTORY_AVAILABLE = "native_history_available"
    const val COLUMN_NATIVE_TEXT_SEND_AVAILABLE = "native_text_send_available"
    const val COLUMN_ARBITRARY_ROUTE_OVERLAY_AVAILABLE = "arbitrary_route_overlay_available"

    const val COLUMN_DIRECTION = "direction"
    const val COLUMN_VERSION = "version"
    const val COLUMN_HEADER_MSG_ID = "header_msg_id"
    const val COLUMN_RAW_BYTES = "raw_bytes"
    const val COLUMN_PACKET_ID = "packet_id"
    const val COLUMN_FROM = "from"
    const val COLUMN_TO = "to"
    const val COLUMN_CHANNEL_INDEX = "channel_index"
    const val COLUMN_PORT_NUM = "port_num"
    const val COLUMN_CACHED_AT_MILLIS = "cached_at_millis"

    const val COLUMN_NODE_NUM = "node_num"
    const val COLUMN_NODE_ID = "node_id"
    const val COLUMN_SHORT_NAME = "short_name"
    const val COLUMN_LONG_NAME = "long_name"
    const val COLUMN_LAST_HEARD = "last_heard"
    const val COLUMN_SNR = "snr"
    const val COLUMN_RSSI = "rssi"
    const val COLUMN_HOPS_AWAY = "hops_away"
    const val COLUMN_BATTERY_LEVEL = "battery_level"
    const val COLUMN_VOLTAGE = "voltage"
    const val COLUMN_IS_ONLINE = "is_online"
    const val COLUMN_HARDWARE_MODEL = "hardware_model"
    const val COLUMN_ROLE = "role"

    const val COLUMN_CHANNEL_NAME = "channel_name"
    const val COLUMN_SOURCE_CHANNEL_ID = "source_channel_id"
    const val COLUMN_ROUTE_TOKEN = "route_token"
    const val COLUMN_SLOT_INDEX = "slot_index"
    const val COLUMN_CONFIGURED_NAME = "configured_name"
    const val COLUMN_DISPLAY_NAME = "display_name"
    const val COLUMN_SECURITY_CLASS = "security_class"
    const val COLUMN_UPLINK_ENABLED = "uplink_enabled"
    const val COLUMN_DOWNLINK_ENABLED = "downlink_enabled"
    const val COLUMN_CAN_READ_NATIVE_TEXT = "can_read_native_text"
    const val COLUMN_CAN_SEND_NATIVE_TEXT = "can_send_native_text"
    const val COLUMN_CAN_SEND_NT_OVERLAY = "can_send_nt_overlay"

    const val COLUMN_SOURCE_MESSAGE_ID = "source_message_id"
    const val COLUMN_ORIGIN_CLIENT_MESSAGE_ID = "origin_client_message_id"
    const val COLUMN_CHANGE_SEQ = "change_seq"
    const val COLUMN_FROM_NODE_ID = "from_node_id"
    const val COLUMN_FROM_DISPLAY_NAME = "from_display_name"
    const val COLUMN_TEXT = "text"
    const val COLUMN_SENDER_TIMESTAMP_MILLIS = "sender_timestamp_millis"
    const val COLUMN_RECEIVED_AT_MILLIS = "received_at_millis"
    const val COLUMN_STATUS = "status"
    const val COLUMN_VIA_MQTT = "via_mqtt"

    const val MIME_STATUS = "vnd.android.cursor.item/vnd.$PREFIX.gateway.status"
    const val MIME_ENVELOPES = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.envelope"
    const val MIME_NODES = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.node"
    const val MIME_CHANNELS = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.channel"
    const val MIME_MESSAGE_CHANGES = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.message-change"

    fun authorityFor(applicationId: String): String = applicationId + AUTHORITY_SUFFIX

    fun statusUri(authority: String): Uri = endpointUri(authority, PATH_STATUS)

    fun envelopesUri(authority: String): Uri = endpointUri(authority, PATH_ENVELOPES)

    fun nodesUri(authority: String): Uri = endpointUri(authority, PATH_NODES)

    fun channelsUri(authority: String): Uri = endpointUri(authority, PATH_CHANNELS)

    fun v2StatusUri(authority: String): Uri = v2EndpointUri(authority, PATH_STATUS)

    fun v2ChannelsUri(authority: String): Uri = v2EndpointUri(authority, PATH_CHANNELS)

    fun v2MessageChangesUri(authority: String, after: Long = 0, limit: Int = 100): Uri =
        v2EndpointUri(authority, PATH_MESSAGE_CHANGES)
            .buildUpon()
            .appendQueryParameter(QUERY_AFTER, after.toString())
            .appendQueryParameter(QUERY_LIMIT, limit.toString())
            .build()

    private fun endpointUri(authority: String, path: String): Uri =
        Uri.Builder().scheme("content").authority(authority).appendPath(PATH_VERSION).appendPath(path).build()

    private fun v2EndpointUri(authority: String, path: String): Uri =
        Uri.Builder().scheme("content").authority(authority).appendPath(PATH_VERSION_V2).appendPath(path).build()
}
