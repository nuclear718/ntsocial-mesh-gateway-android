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
package com.ntsocial.meshlink.core.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Read-only, certificate-pinned gateway snapshots for the NTsocial Android application.
 *
 * This Provider intentionally does not expose radio configuration, PSKs, raw node protobufs, positions, or message
 * payload outside the validated envelope BLOB. It never resolves Koin during [onCreate], because Android may create a
 * Provider before the application's Koin startup is complete.
 */
class NtsocialGatewayProvider :
    ContentProvider(),
    KoinComponent {
    private val callerVerifier: NtsocialGatewayCallerVerifier by inject()
    private val capabilityStore: NtsocialGatewayCommandCapabilityStore by inject()
    private val eventPublisher: NtsocialGatewayEventPublisher by inject()
    private val gatewayRepository: NtsocialGatewayRepository by inject()
    private val serviceRepository: ServiceRepository by inject()
    private val nodeRepository: NodeRepository by inject()
    private val cursorFactory by lazy {
        NtsocialGatewaySnapshotCursorFactory(
            gatewayRepository = gatewayRepository,
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            eventPublisher = eventPublisher,
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        enforceAccess()
        require(selection == null && selectionArgs == null && sortOrder == null) {
            "Gateway snapshot queries do not support selection or sort arguments"
        }

        return cursorFactory.create(endpointFor(uri), projection)
    }

    override fun getType(uri: Uri): String {
        enforceAccess()
        return when (endpointFor(uri)) {
            GatewayEndpoint.STATUS -> NtsocialGatewayContract.MIME_STATUS
            GatewayEndpoint.ENVELOPES -> NtsocialGatewayContract.MIME_ENVELOPES
            GatewayEndpoint.NODES -> NtsocialGatewayContract.MIME_NODES
            GatewayEndpoint.CHANNELS -> NtsocialGatewayContract.MIME_CHANNELS
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        enforceAccess()
        throw UnsupportedOperationException("NTsocial Gateway Provider is read-only")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        enforceAccess()
        throw UnsupportedOperationException("NTsocial Gateway Provider is read-only")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        enforceAccess()
        throw UnsupportedOperationException("NTsocial Gateway Provider is read-only")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val caller = enforceAccess()
        return when (method) {
            NtsocialGatewayContract.METHOD_ISSUE_COMMAND_CAPABILITY -> {
                val requestId = extras?.getString(NtsocialGatewayContract.EXTRA_REQUEST_ID)
                require(!requestId.isNullOrBlank() && requestId.length <= MAX_REQUEST_ID_LENGTH) {
                    "A bounded request_id is required"
                }
                capabilityStore.issue(caller, requestId).let { capability ->
                    Bundle().apply {
                        putString(NtsocialGatewayContract.EXTRA_AUTHORIZATION_TOKEN, capability.token)
                        putLong(
                            NtsocialGatewayContract.EXTRA_AUTHORIZATION_EXPIRES_AT_MILLIS,
                            capability.expiresAtMillis,
                        )
                    }
                }
            }

            else -> super.call(method, arg, extras)
        }
    }

    private fun endpointFor(uri: Uri): GatewayEndpoint {
        val expectedAuthority = NtsocialGatewayContract.authorityFor(requireContext().packageName)
        require(uri.authority == expectedAuthority) { "Unknown NTsocial Gateway authority" }
        return when (uri.pathSegments) {
            listOf(NtsocialGatewayContract.PATH_VERSION, NtsocialGatewayContract.PATH_STATUS) -> GatewayEndpoint.STATUS

            listOf(NtsocialGatewayContract.PATH_VERSION, NtsocialGatewayContract.PATH_ENVELOPES) ->
                GatewayEndpoint.ENVELOPES

            listOf(NtsocialGatewayContract.PATH_VERSION, NtsocialGatewayContract.PATH_NODES) -> GatewayEndpoint.NODES

            listOf(NtsocialGatewayContract.PATH_VERSION, NtsocialGatewayContract.PATH_CHANNELS) ->
                GatewayEndpoint.CHANNELS

            else -> throw IllegalArgumentException("Unknown NTsocial Gateway URI")
        }
    }

    private fun enforceAccess(): NtsocialGatewayCaller =
        callerVerifier.requireTrustedCaller(Binder.getCallingUid(), getCallingPackage())

    private companion object {
        const val MAX_REQUEST_ID_LENGTH = 128
    }
}

private class NtsocialGatewaySnapshotCursorFactory(
    private val gatewayRepository: NtsocialGatewayRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val eventPublisher: NtsocialGatewayEventPublisher,
) {
    fun create(endpoint: GatewayEndpoint, projection: Array<String>?): Cursor = when (endpoint) {
        GatewayEndpoint.STATUS -> statusCursor(projection)
        GatewayEndpoint.ENVELOPES -> envelopesCursor(projection)
        GatewayEndpoint.NODES -> nodesCursor(projection)
        GatewayEndpoint.CHANNELS -> channelsCursor(projection)
    }

    private fun statusCursor(projection: Array<String>?): Cursor {
        val defaultChannel = gatewayRepository.defaultChannelStatus.value
        val localNode = nodeRepository.ourNodeInfo.value
        val nodeInfo = nodeRepository.myNodeInfo.value
        val values: Map<String, Any?> =
            mapOf(
                NtsocialGatewayContract.COLUMN_API_VERSION to NtsocialGatewayContract.API_VERSION,
                NtsocialGatewayContract.COLUMN_CONNECTION_STATE to
                    serviceRepository.connectionState.value.toStatusName(),
                NtsocialGatewayContract.COLUMN_CACHED_ENVELOPE_COUNT to gatewayRepository.cachedEnvelopes.value.size,
                NtsocialGatewayContract.COLUMN_CACHED_ENVELOPE_LIMIT to
                    com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport.MAX_CACHED_ENVELOPES,
                NtsocialGatewayContract.COLUMN_PRIVATE_APP_PORT_NUM to
                    com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport.PRIVATE_APP_PORT_NUM,
                NtsocialGatewayContract.COLUMN_LEGACY_RECEIVE_ONLY_PORT_NUM to
                    com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM,
                NtsocialGatewayContract.COLUMN_MAX_ENVELOPE_SIZE_BYTES to
                    com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport.MAX_ENVELOPE_SIZE_BYTES,
                NtsocialGatewayContract.COLUMN_MAX_PAYLOAD_SIZE_BYTES to
                    com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES,
                NtsocialGatewayContract.COLUMN_MAX_COMMAND_ENVELOPE_SIZE_BYTES to
                    com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES,
                NtsocialGatewayContract.COLUMN_DEFAULT_CHANNEL_READY to defaultChannel.ready.asInt(),
                NtsocialGatewayContract.COLUMN_DEFAULT_CHANNEL_INDEX to defaultChannel.channelIndex,
                NtsocialGatewayContract.COLUMN_PROVISIONING_STATE to defaultChannel.provisioningState,
                NtsocialGatewayContract.COLUMN_PROVISIONING_CHANNEL_CHANGE to defaultChannel.provisioningChannelChange,
                NtsocialGatewayContract.COLUMN_PROVISIONING_LORA_APPLIED to
                    defaultChannel.provisioningLoraApplied?.asInt(),
                NtsocialGatewayContract.COLUMN_LOCAL_NODE_ID to nodeRepository.myId.value,
                NtsocialGatewayContract.COLUMN_GATEWAY_BATTERY_PERCENT to localNode?.batteryLevel,
                NtsocialGatewayContract.COLUMN_GATEWAY_VOLTAGE to localNode?.voltage,
                NtsocialGatewayContract.COLUMN_GATEWAY_CHANNEL_UTILIZATION to
                    (nodeInfo?.channelUtilization ?: localNode?.deviceMetrics?.channel_utilization),
                NtsocialGatewayContract.COLUMN_GATEWAY_AIR_UTIL_TX to
                    (nodeInfo?.airUtilTx ?: localNode?.deviceMetrics?.air_util_tx),
            )
        return singleRowCursor(projection, STATUS_COLUMNS, values)
    }

    private fun envelopesCursor(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(resolveProjection(projection, ENVELOPE_COLUMNS))
        gatewayRepository.cachedEnvelopes.value.forEach { envelope ->
            cursor.addValues(
                mapOf(
                    NtsocialGatewayContract.COLUMN_DIRECTION to envelope.direction.name,
                    NtsocialGatewayContract.COLUMN_VERSION to envelope.envelope.version,
                    NtsocialGatewayContract.COLUMN_HEADER_MSG_ID to envelope.envelope.headerMsgId.toByteArray(),
                    NtsocialGatewayContract.COLUMN_RAW_BYTES to envelope.rawBytes.toByteArray(),
                    NtsocialGatewayContract.COLUMN_PACKET_ID to envelope.packetId,
                    NtsocialGatewayContract.COLUMN_FROM to envelope.from,
                    NtsocialGatewayContract.COLUMN_TO to envelope.to,
                    NtsocialGatewayContract.COLUMN_CHANNEL_INDEX to envelope.channelIndex,
                    NtsocialGatewayContract.COLUMN_PORT_NUM to envelope.portNum,
                    NtsocialGatewayContract.COLUMN_CACHED_AT_MILLIS to envelope.cachedAtMillis,
                ),
            )
        }
        return cursor
    }

    private fun nodesCursor(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(resolveProjection(projection, NODE_COLUMNS))
        nodeRepository.nodeDBbyNum.value.values
            .sortedBy { it.num }
            .forEach { node ->
                cursor.addValues(
                    mapOf(
                        NtsocialGatewayContract.COLUMN_NODE_NUM to node.num,
                        NtsocialGatewayContract.COLUMN_NODE_ID to node.user.id,
                        NtsocialGatewayContract.COLUMN_SHORT_NAME to node.user.short_name,
                        NtsocialGatewayContract.COLUMN_LONG_NAME to node.user.long_name,
                        NtsocialGatewayContract.COLUMN_LAST_HEARD to node.lastHeard,
                        NtsocialGatewayContract.COLUMN_SNR to node.snr,
                        NtsocialGatewayContract.COLUMN_RSSI to node.rssi,
                        NtsocialGatewayContract.COLUMN_HOPS_AWAY to node.hopsAway,
                        NtsocialGatewayContract.COLUMN_CHANNEL_INDEX to node.channel,
                        NtsocialGatewayContract.COLUMN_BATTERY_LEVEL to node.batteryLevel,
                        NtsocialGatewayContract.COLUMN_VOLTAGE to node.voltage,
                        NtsocialGatewayContract.COLUMN_IS_ONLINE to node.isOnline.asInt(),
                        NtsocialGatewayContract.COLUMN_HARDWARE_MODEL to node.user.hw_model.name,
                        NtsocialGatewayContract.COLUMN_ROLE to node.user.role.name,
                    ),
                )
            }
        return cursor
    }

    private fun channelsCursor(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(resolveProjection(projection, CHANNEL_COLUMNS))
        eventPublisher.channelSet.value.settings.forEachIndexed { index, settings ->
            cursor.addValues(
                mapOf(
                    NtsocialGatewayContract.COLUMN_CHANNEL_INDEX to index,
                    NtsocialGatewayContract.COLUMN_CHANNEL_NAME to settings.name,
                    NtsocialGatewayContract.COLUMN_UPLINK_ENABLED to settings.uplink_enabled.asInt(),
                    NtsocialGatewayContract.COLUMN_DOWNLINK_ENABLED to settings.downlink_enabled.asInt(),
                ),
            )
        }
        return cursor
    }

    private fun singleRowCursor(
        projection: Array<String>?,
        supportedColumns: Array<String>,
        values: Map<String, Any?>,
    ): Cursor =
        MatrixCursor(resolveProjection(projection, supportedColumns)).also { cursor -> cursor.addValues(values) }

    private fun MatrixCursor.addValues(values: Map<String, Any?>) {
        val row = newRow()
        columnNames.forEach { columnName -> row.add(values[columnName]) }
    }

    private fun resolveProjection(projection: Array<String>?, supportedColumns: Array<String>): Array<String> =
        projection ?: supportedColumns

    private companion object {
        val STATUS_COLUMNS =
            arrayOf(
                NtsocialGatewayContract.COLUMN_API_VERSION,
                NtsocialGatewayContract.COLUMN_CONNECTION_STATE,
                NtsocialGatewayContract.COLUMN_CACHED_ENVELOPE_COUNT,
                NtsocialGatewayContract.COLUMN_CACHED_ENVELOPE_LIMIT,
                NtsocialGatewayContract.COLUMN_PRIVATE_APP_PORT_NUM,
                NtsocialGatewayContract.COLUMN_LEGACY_RECEIVE_ONLY_PORT_NUM,
                NtsocialGatewayContract.COLUMN_MAX_ENVELOPE_SIZE_BYTES,
                NtsocialGatewayContract.COLUMN_MAX_PAYLOAD_SIZE_BYTES,
                NtsocialGatewayContract.COLUMN_MAX_COMMAND_ENVELOPE_SIZE_BYTES,
                NtsocialGatewayContract.COLUMN_DEFAULT_CHANNEL_READY,
                NtsocialGatewayContract.COLUMN_DEFAULT_CHANNEL_INDEX,
                NtsocialGatewayContract.COLUMN_PROVISIONING_STATE,
                NtsocialGatewayContract.COLUMN_PROVISIONING_CHANNEL_CHANGE,
                NtsocialGatewayContract.COLUMN_PROVISIONING_LORA_APPLIED,
                NtsocialGatewayContract.COLUMN_LOCAL_NODE_ID,
                NtsocialGatewayContract.COLUMN_GATEWAY_BATTERY_PERCENT,
                NtsocialGatewayContract.COLUMN_GATEWAY_VOLTAGE,
                NtsocialGatewayContract.COLUMN_GATEWAY_CHANNEL_UTILIZATION,
                NtsocialGatewayContract.COLUMN_GATEWAY_AIR_UTIL_TX,
            )

        val ENVELOPE_COLUMNS =
            arrayOf(
                NtsocialGatewayContract.COLUMN_DIRECTION,
                NtsocialGatewayContract.COLUMN_VERSION,
                NtsocialGatewayContract.COLUMN_HEADER_MSG_ID,
                NtsocialGatewayContract.COLUMN_RAW_BYTES,
                NtsocialGatewayContract.COLUMN_PACKET_ID,
                NtsocialGatewayContract.COLUMN_FROM,
                NtsocialGatewayContract.COLUMN_TO,
                NtsocialGatewayContract.COLUMN_CHANNEL_INDEX,
                NtsocialGatewayContract.COLUMN_PORT_NUM,
                NtsocialGatewayContract.COLUMN_CACHED_AT_MILLIS,
            )

        val NODE_COLUMNS =
            arrayOf(
                NtsocialGatewayContract.COLUMN_NODE_NUM,
                NtsocialGatewayContract.COLUMN_NODE_ID,
                NtsocialGatewayContract.COLUMN_SHORT_NAME,
                NtsocialGatewayContract.COLUMN_LONG_NAME,
                NtsocialGatewayContract.COLUMN_LAST_HEARD,
                NtsocialGatewayContract.COLUMN_SNR,
                NtsocialGatewayContract.COLUMN_RSSI,
                NtsocialGatewayContract.COLUMN_HOPS_AWAY,
                NtsocialGatewayContract.COLUMN_CHANNEL_INDEX,
                NtsocialGatewayContract.COLUMN_BATTERY_LEVEL,
                NtsocialGatewayContract.COLUMN_VOLTAGE,
                NtsocialGatewayContract.COLUMN_IS_ONLINE,
                NtsocialGatewayContract.COLUMN_HARDWARE_MODEL,
                NtsocialGatewayContract.COLUMN_ROLE,
            )

        val CHANNEL_COLUMNS =
            arrayOf(
                NtsocialGatewayContract.COLUMN_CHANNEL_INDEX,
                NtsocialGatewayContract.COLUMN_CHANNEL_NAME,
                NtsocialGatewayContract.COLUMN_UPLINK_ENABLED,
                NtsocialGatewayContract.COLUMN_DOWNLINK_ENABLED,
            )
    }
}

private enum class GatewayEndpoint {
    STATUS,
    ENVELOPES,
    NODES,
    CHANNELS,
}

private fun Boolean.asInt(): Int = if (this) 1 else 0

private fun ConnectionState.toStatusName(): String = when (this) {
    ConnectionState.Connected -> "CONNECTED"
    ConnectionState.Connecting -> "CONNECTING"
    ConnectionState.DeviceSleep -> "DEVICE_SLEEP"
    ConnectionState.Disconnected -> "DISCONNECTED"
}
