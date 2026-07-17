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
package com.ntsocial.meshlink.core.meshcore

/** Connection lifecycle owned by the future MeshCore transport, independent from Meshtastic connection state. */
enum class MeshCoreConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    SYNCHRONIZING,
    CONNECTED,
    ERROR,
}

/** Companion transports supported by the official MeshCore firmware. */
enum class MeshCoreTransport {
    BLE,
    USB,
    TCP,
}

/** Advertised MeshCore contact types. */
@Suppress("MagicNumber")
enum class MeshCoreContactType(val wireValue: Int) {
    UNKNOWN(0),
    CHAT(1),
    REPEATER(2),
    ROOM_SERVER(3),
    SENSOR(4),
    ;

    companion object {
        fun fromWireValue(value: Int): MeshCoreContactType = entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

enum class MeshCoreMessageKind {
    DIRECT,
    CHANNEL,
}

enum class MeshCoreMessageDirection {
    RECEIVED,
    SENT,
}

enum class MeshCoreMessageStatus {
    RECEIVED,
    QUEUED,
    SENT,
    CONFIRMED,
    FAILED,
}

enum class MeshCorePathMode {
    DIRECT,
    FLOOD,
    ROUTED,
}

/** Decoded path metadata. The firmware encodes hash width in the top two bits and hop count in the lower six. */
data class MeshCorePath(
    val mode: MeshCorePathMode,
    val encodedValue: Int,
    val hopCount: Int?,
    val hashSizeBytes: Int?,
) {
    companion object {
        fun received(encodedValue: Int): MeshCorePath = if (encodedValue == DIRECT_SENTINEL) {
            MeshCorePath(MeshCorePathMode.DIRECT, encodedValue, hopCount = null, hashSizeBytes = null)
        } else {
            MeshCorePath(
                mode = MeshCorePathMode.FLOOD,
                encodedValue = encodedValue,
                hopCount = encodedValue and HOP_COUNT_MASK,
                hashSizeBytes = (encodedValue shr HASH_SIZE_SHIFT) + 1,
            )
        }

        fun outbound(encodedValue: Int): MeshCorePath = if (encodedValue == DIRECT_SENTINEL) {
            MeshCorePath(MeshCorePathMode.FLOOD, encodedValue, hopCount = null, hashSizeBytes = null)
        } else {
            MeshCorePath(
                mode = MeshCorePathMode.ROUTED,
                encodedValue = encodedValue,
                hopCount = encodedValue and HOP_COUNT_MASK,
                hashSizeBytes = (encodedValue shr HASH_SIZE_SHIFT) + 1,
            )
        }
    }
}

data class MeshCoreRadioSettings(
    val frequencyKhz: Long,
    val bandwidthHz: Long,
    val spreadingFactor: Int,
    val codingRate: Int,
    val txPowerDbm: Int,
    val maxTxPowerDbm: Int,
)

data class MeshCoreSelfInfo(
    val name: String,
    val advertType: MeshCoreContactType,
    val publicKey: ByteArray,
    val advertisedLatitudeE6: Int,
    val advertisedLongitudeE6: Int,
    val radio: MeshCoreRadioSettings,
    val manualAddContacts: Boolean,
    val multiAcks: Int,
    val advertisedLocationPolicy: Int,
    val telemetryModeBase: Int,
    val telemetryModeLocation: Int,
    val telemetryModeEnvironment: Int,
)

data class MeshCoreDeviceInfo(
    val protocolVersion: Int,
    val maxContacts: Int?,
    val maxChannels: Int?,
    val blePin: Long?,
    val firmwareBuild: String?,
    val model: String?,
    val firmwareVersion: String?,
    val repeatEnabled: Boolean?,
    val pathHashMode: Int?,
)

data class MeshCoreContact(
    val publicKey: ByteArray,
    val type: MeshCoreContactType,
    val flags: Int,
    val outboundPath: MeshCorePath,
    val outboundPathBytes: ByteArray,
    val name: String,
    val lastAdvertEpochSeconds: Long,
    val advertisedLatitudeE6: Int,
    val advertisedLongitudeE6: Int,
    val lastModifiedEpochSeconds: Long,
)

/** Channel secrets must never be rendered, logged, or exposed through Gateway events/providers. */
data class MeshCoreChannel(val index: Int, val name: String, val secret: ByteArray)

data class MeshCoreMessage(
    val id: String,
    val conversationId: String,
    val kind: MeshCoreMessageKind,
    val text: String,
    val senderTimestamp: Long,
    val direction: MeshCoreMessageDirection,
    val status: MeshCoreMessageStatus,
    val path: MeshCorePath?,
    val snrDb: Float?,
    val signed: Boolean,
)

internal const val DIRECT_SENTINEL = 0xFF
internal const val HOP_COUNT_MASK = 0x3F
internal const val HASH_SIZE_SHIFT = 6
