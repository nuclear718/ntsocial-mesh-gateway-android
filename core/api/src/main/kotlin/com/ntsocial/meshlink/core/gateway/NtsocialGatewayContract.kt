/*
 * Copyright (c) 2026 Meshtastic LLC
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

/**
 * Versioned NTsocial MeshLink Gateway IPC contract.
 *
 * The Binder contract remains only for transitional compatibility. New integrations use the protected Provider for
 * snapshots, an explicit command broadcast for outbound packets, and metadata-only events to prompt re-queries.
 */
object NtsocialGatewayContract {
    private const val PREFIX = "com.ntsocial.meshlink"

    const val API_VERSION = 1
    const val AUTHORITY_SUFFIX = ".gateway"

    const val PATH_VERSION = "v1"
    const val PATH_STATUS = "status"
    const val PATH_ENVELOPES = "envelopes"
    const val PATH_NODES = "nodes"
    const val PATH_CHANNELS = "channels"

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
    const val EXTRA_PAYLOAD = "payload"
    const val EXTRA_CHANNEL_INDEX = "channel_index"
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
    const val COLUMN_UPLINK_ENABLED = "uplink_enabled"
    const val COLUMN_DOWNLINK_ENABLED = "downlink_enabled"

    const val MIME_STATUS = "vnd.android.cursor.item/vnd.$PREFIX.gateway.status"
    const val MIME_ENVELOPES = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.envelope"
    const val MIME_NODES = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.node"
    const val MIME_CHANNELS = "vnd.android.cursor.dir/vnd.$PREFIX.gateway.channel"

    fun authorityFor(applicationId: String): String = applicationId + AUTHORITY_SUFFIX

    fun statusUri(authority: String): Uri = endpointUri(authority, PATH_STATUS)

    fun envelopesUri(authority: String): Uri = endpointUri(authority, PATH_ENVELOPES)

    fun nodesUri(authority: String): Uri = endpointUri(authority, PATH_NODES)

    fun channelsUri(authority: String): Uri = endpointUri(authority, PATH_CHANNELS)

    private fun endpointUri(authority: String, path: String): Uri =
        Uri.Builder().scheme("content").authority(authority).appendPath(PATH_VERSION).appendPath(path).build()
}
