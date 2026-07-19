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
package com.ntsocial.meshlink.core.meshcore

/**
 * Bounds-checked codec for the official MeshCore Companion protocol.
 *
 * It follows the v1.16.0 firmware framing and current official client initialization (app target protocol 3). BLE
 * framing is one characteristic value per frame; USB/TCP envelope framing belongs in the transport layer.
 */
@Suppress("TooManyFunctions")
object MeshCoreCompanionProtocol {
    const val BLE_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    const val BLE_RX_CHARACTERISTIC_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    const val BLE_TX_CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
    const val APP_TARGET_PROTOCOL_VERSION = 3
    const val MAX_FRAME_SIZE = 176
    const val CHANNEL_SLOT_COUNT = 8
    const val MAX_CHANNEL_NAME_BYTES = 32
    const val CHANNEL_SECRET_BYTES = 16

    fun appStart(applicationName: String = "NTsocial MeshLink"): ByteArray {
        val nameBytes = applicationName.encodeToByteArray()
        return frame(
            Command.APP_START,
            byteArrayOf(APP_TARGET_PROTOCOL_VERSION.toByte()),
            ByteArray(APP_START_RESERVED_BYTES),
            nameBytes,
        )
    }

    fun deviceQuery(): ByteArray = frame(Command.DEVICE_QUERY, byteArrayOf(APP_TARGET_PROTOCOL_VERSION.toByte()))

    fun getContacts(sinceEpochSeconds: Long? = null): ByteArray = if (sinceEpochSeconds == null) {
        frame(Command.GET_CONTACTS)
    } else {
        frame(Command.GET_CONTACTS, littleEndianUInt32(sinceEpochSeconds))
    }

    fun getChannel(index: Int): ByteArray {
        requireChannelIndex(index)
        return frame(Command.GET_CHANNEL, byteArrayOf(index.toByte()))
    }

    fun setChannel(index: Int, name: String, secret: ByteArray): ByteArray {
        requireChannelIndex(index)
        val nameBytes = name.encodeToByteArray()
        require(nameBytes.size <= MAX_CHANNEL_NAME_BYTES) {
            "MeshCore channel names are limited to $MAX_CHANNEL_NAME_BYTES UTF-8 bytes"
        }
        require(secret.size == CHANNEL_SECRET_BYTES) {
            "MeshCore channel secrets must contain exactly $CHANNEL_SECRET_BYTES bytes"
        }
        return frame(
            Command.SET_CHANNEL,
            byteArrayOf(index.toByte()),
            nameBytes.copyOf(MAX_CHANNEL_NAME_BYTES),
            secret.copyOf(),
        )
    }

    fun syncNextMessage(): ByteArray = frame(Command.SYNC_NEXT_MESSAGE)

    fun getBatteryAndStorage(): ByteArray = frame(Command.GET_BATTERY_AND_STORAGE)

    fun getDeviceTime(): ByteArray = frame(Command.GET_DEVICE_TIME)

    fun setDeviceTime(epochSeconds: Long): ByteArray = frame(Command.SET_DEVICE_TIME, littleEndianUInt32(epochSeconds))

    fun setAdvertName(name: String): ByteArray = frame(Command.SET_ADVERT_NAME, name.encodeToByteArray())

    fun setAdvertisedCoordinates(latitudeE6: Int, longitudeE6: Int): ByteArray = frame(
        Command.SET_ADVERT_LAT_LON,
        littleEndianInt32(latitudeE6),
        littleEndianInt32(longitudeE6),
        littleEndianInt32(0),
    )

    fun setRadioParameters(
        frequencyKhz: Long,
        bandwidthHz: Long,
        spreadingFactor: Int,
        codingRate: Int,
        repeatEnabled: Boolean? = null,
    ): ByteArray {
        require(spreadingFactor in MIN_SPREADING_FACTOR..MAX_SPREADING_FACTOR)
        require(codingRate in MIN_CODING_RATE..MAX_CODING_RATE)
        val repeat = repeatEnabled?.let { byteArrayOf(if (it) 1 else 0) } ?: byteArrayOf()
        return frame(
            Command.SET_RADIO_PARAMETERS,
            littleEndianUInt32(frequencyKhz),
            littleEndianUInt32(bandwidthHz),
            byteArrayOf(spreadingFactor.toByte(), codingRate.toByte()),
            repeat,
        )
    }

    fun setTxPower(txPowerDbm: Int): ByteArray = frame(Command.SET_TX_POWER, littleEndianInt32(txPowerDbm))

    fun sendChannelMessage(channelIndex: Int, senderTimestamp: Long, text: String): ByteArray {
        requireChannelIndex(channelIndex)
        return frame(
            Command.SEND_CHANNEL_TEXT,
            byteArrayOf(TextType.PLAIN, channelIndex.toByte()),
            littleEndianUInt32(senderTimestamp),
            text.encodeToByteArray(),
        )
    }

    fun sendDirectMessage(publicKey: ByteArray, senderTimestamp: Long, text: String, attempt: Int = 0): ByteArray {
        require(publicKey.size >= PUBLIC_KEY_PREFIX_BYTES)
        require(attempt in 0..MAX_ATTEMPT)
        return frame(
            Command.SEND_DIRECT_TEXT,
            byteArrayOf(TextType.PLAIN, attempt.toByte()),
            littleEndianUInt32(senderTimestamp),
            publicKey.copyOf(PUBLIC_KEY_PREFIX_BYTES),
            text.encodeToByteArray(),
        )
    }

    fun parseFrame(bytes: ByteArray): MeshCoreFrame {
        require(bytes.isNotEmpty()) { "MeshCore frame is empty" }
        require(bytes.size <= MAX_FRAME_SIZE) { "MeshCore frame exceeds $MAX_FRAME_SIZE bytes" }
        val reader = MeshCoreByteReader(bytes)
        val code = reader.readUnsignedByte()
        return if (code == Push.MESSAGES_WAITING) MeshCoreFrame.MessagesWaiting else parseResponseFrame(code, reader)
    }

    private fun parseResponseFrame(code: Int, reader: MeshCoreByteReader): MeshCoreFrame = when (code) {
        Response.OK -> MeshCoreFrame.Ok(if (reader.remaining >= UINT32_BYTES) reader.readUInt32() else null)

        Response.ERROR ->
            MeshCoreFrame.Error(if (reader.remaining > 0) reader.readUnsignedByte().toMeshCoreError() else null)

        Response.CONTACTS_START -> MeshCoreFrame.ContactsStart(reader.readUInt32())

        Response.CONTACT -> MeshCoreFrame.Contact(parseContact(reader))

        Response.END_OF_CONTACTS -> MeshCoreFrame.ContactsEnd(reader.readUInt32())

        Response.SELF_INFO -> MeshCoreFrame.SelfInfo(parseSelfInfo(reader))

        Response.CONTACT_MESSAGE,
        Response.CHANNEL_MESSAGE,
        Response.CONTACT_MESSAGE_V3,
        Response.CHANNEL_MESSAGE_V3,
        -> parseMessageFrame(code, reader)

        Response.NO_MORE_MESSAGES -> MeshCoreFrame.NoMoreMessages

        Response.BATTERY_AND_STORAGE -> parseBattery(reader)

        Response.DEVICE_INFO -> MeshCoreFrame.DeviceInfo(parseDeviceInfo(reader))

        Response.CHANNEL_INFO -> MeshCoreFrame.Channel(parseChannel(reader))

        else -> MeshCoreFrame.Unknown(code, reader.readRemaining())
    }

    private fun parseMessageFrame(code: Int, reader: MeshCoreByteReader): MeshCoreFrame = when (code) {
        Response.CONTACT_MESSAGE -> parseDirectMessage(reader, hasSignalMetadata = false)
        Response.CHANNEL_MESSAGE -> parseChannelMessage(reader, hasSignalMetadata = false)
        Response.CONTACT_MESSAGE_V3 -> parseDirectMessage(reader, hasSignalMetadata = true)
        Response.CHANNEL_MESSAGE_V3 -> parseChannelMessage(reader, hasSignalMetadata = true)
        else -> error("Not a MeshCore message response: $code")
    }

    private fun parseSelfInfo(reader: MeshCoreByteReader): MeshCoreSelfInfo {
        val advertType = MeshCoreContactType.fromWireValue(reader.readUnsignedByte())
        val txPower = reader.readUnsignedByte()
        val maxTxPower = reader.readUnsignedByte()
        val publicKey = reader.readBytes(PUBLIC_KEY_BYTES)
        val latitudeE6 = reader.readInt32()
        val longitudeE6 = reader.readInt32()
        val multiAcks = reader.readUnsignedByte()
        val locationPolicy = reader.readUnsignedByte()
        val telemetryMode = reader.readUnsignedByte()
        val manualAddContacts = reader.readUnsignedByte() != 0
        val frequencyKhz = reader.readUInt32()
        val bandwidthHz = reader.readUInt32()
        val spreadingFactor = reader.readUnsignedByte()
        val codingRate = reader.readUnsignedByte()
        val name = reader.readRemaining().decodeUtf8NullTerminated()
        return MeshCoreSelfInfo(
            name = name,
            advertType = advertType,
            publicKey = publicKey,
            advertisedLatitudeE6 = latitudeE6,
            advertisedLongitudeE6 = longitudeE6,
            radio =
            MeshCoreRadioSettings(
                frequencyKhz = frequencyKhz,
                bandwidthHz = bandwidthHz,
                spreadingFactor = spreadingFactor,
                codingRate = codingRate,
                txPowerDbm = txPower,
                maxTxPowerDbm = maxTxPower,
            ),
            manualAddContacts = manualAddContacts,
            multiAcks = multiAcks,
            advertisedLocationPolicy = locationPolicy,
            telemetryModeBase = telemetryMode and TELEMETRY_MODE_MASK,
            telemetryModeLocation = (telemetryMode shr TELEMETRY_LOCATION_SHIFT) and TELEMETRY_MODE_MASK,
            telemetryModeEnvironment = (telemetryMode shr TELEMETRY_ENVIRONMENT_SHIFT) and TELEMETRY_MODE_MASK,
        )
    }

    private fun parseDeviceInfo(reader: MeshCoreByteReader): MeshCoreDeviceInfo {
        val protocolVersion = reader.readUnsignedByte()
        if (protocolVersion < DEVICE_INFO_EXTENDED_VERSION) {
            return MeshCoreDeviceInfo(protocolVersion, null, null, null, null, null, null, null, null)
        }
        val maxContacts = reader.readUnsignedByte() * 2
        val maxChannels = reader.readUnsignedByte()
        val blePin = reader.readUInt32()
        val firmwareBuild = reader.readBytes(FIRMWARE_BUILD_BYTES).decodeUtf8NullTerminated()
        val model = reader.readBytes(MODEL_NAME_BYTES).decodeUtf8NullTerminated()
        val firmwareVersion = reader.readBytes(FIRMWARE_VERSION_BYTES).decodeUtf8NullTerminated()
        val repeatEnabled =
            if (protocolVersion >= REPEAT_MODE_VERSION && reader.remaining > 0) {
                reader.readUnsignedByte() != 0
            } else {
                null
            }
        val pathHashMode =
            if (protocolVersion >= PATH_HASH_MODE_VERSION && reader.remaining > 0) reader.readUnsignedByte() else null
        return MeshCoreDeviceInfo(
            protocolVersion = protocolVersion,
            maxContacts = maxContacts,
            maxChannels = maxChannels,
            blePin = blePin,
            firmwareBuild = firmwareBuild,
            model = model,
            firmwareVersion = firmwareVersion,
            repeatEnabled = repeatEnabled,
            pathHashMode = pathHashMode,
        )
    }

    private fun parseContact(reader: MeshCoreByteReader): MeshCoreContact {
        val publicKey = reader.readBytes(PUBLIC_KEY_BYTES)
        val type = MeshCoreContactType.fromWireValue(reader.readUnsignedByte())
        val flags = reader.readUnsignedByte()
        val path = MeshCorePath.outbound(reader.readUnsignedByte())
        val pathBytes = reader.readBytes(MAX_PATH_BYTES).dropTrailingZeros()
        val name = reader.readBytes(CONTACT_NAME_BYTES).decodeUtf8NullTerminated()
        return MeshCoreContact(
            publicKey = publicKey,
            type = type,
            flags = flags,
            outboundPath = path,
            outboundPathBytes = pathBytes,
            name = name,
            lastAdvertEpochSeconds = reader.readUInt32(),
            advertisedLatitudeE6 = reader.readInt32(),
            advertisedLongitudeE6 = reader.readInt32(),
            lastModifiedEpochSeconds = reader.readUInt32(),
        )
    }

    private fun parseDirectMessage(
        reader: MeshCoreByteReader,
        hasSignalMetadata: Boolean,
    ): MeshCoreFrame.DirectMessage {
        val snr = if (hasSignalMetadata) reader.readSignalMetadata() else null
        val publicKeyPrefix = reader.readBytes(PUBLIC_KEY_PREFIX_BYTES)
        val path = MeshCorePath.received(reader.readUnsignedByte())
        val textType = reader.readUnsignedByte()
        val timestamp = reader.readUInt32()
        val signature =
            if (textType == TextType.SIGNED_PLAIN.toInt()) reader.readBytes(SIGNATURE_PREFIX_BYTES) else null
        return MeshCoreFrame.DirectMessage(
            publicKeyPrefix = publicKeyPrefix,
            path = path,
            textType = textType,
            senderTimestamp = timestamp,
            signature = signature,
            text = reader.readRemaining().decodeUtf8NullTerminated(),
            snrDb = snr,
        )
    }

    private fun parseChannelMessage(
        reader: MeshCoreByteReader,
        hasSignalMetadata: Boolean,
    ): MeshCoreFrame.ChannelMessage {
        val snr = if (hasSignalMetadata) reader.readSignalMetadata() else null
        val channelIndex = reader.readUnsignedByte()
        val path = MeshCorePath.received(reader.readUnsignedByte())
        val textType = reader.readUnsignedByte()
        val timestamp = reader.readUInt32()
        return MeshCoreFrame.ChannelMessage(
            channelIndex = channelIndex,
            path = path,
            textType = textType,
            senderTimestamp = timestamp,
            text = reader.readRemaining().decodeUtf8NullTerminated(),
            snrDb = snr,
        )
    }

    private fun parseBattery(reader: MeshCoreByteReader): MeshCoreFrame.BatteryAndStorage {
        val millivolts = reader.readUInt16()
        val usedKb = if (reader.remaining >= UINT32_BYTES * 2) reader.readUInt32() else null
        val totalKb = if (reader.remaining >= UINT32_BYTES) reader.readUInt32() else null
        return MeshCoreFrame.BatteryAndStorage(millivolts, usedKb, totalKb)
    }

    private fun parseChannel(reader: MeshCoreByteReader): MeshCoreChannel {
        val index = reader.readUnsignedByte()
        requireChannelIndex(index)
        val name = reader.readBytes(MAX_CHANNEL_NAME_BYTES).decodeUtf8NullTerminated()
        val secret = reader.readBytes(CHANNEL_SECRET_BYTES)
        return MeshCoreChannel(index, name, secret)
    }

    private fun MeshCoreByteReader.readSignalMetadata(): Float {
        val snrDb = readSignedByte() / SIGNAL_SCALE
        skip(RESERVED_SIGNAL_BYTES)
        return snrDb
    }

    private fun frame(command: Int, vararg payloads: ByteArray): ByteArray {
        val size = 1 + payloads.sumOf(ByteArray::size)
        require(size <= MAX_FRAME_SIZE) { "MeshCore frame exceeds $MAX_FRAME_SIZE bytes" }
        val result = ByteArray(size)
        result[0] = command.toByte()
        var offset = 1
        payloads.forEach { payload ->
            payload.copyInto(result, destinationOffset = offset)
            offset += payload.size
        }
        return result
    }

    private fun requireChannelIndex(index: Int) {
        require(index in 0 until CHANNEL_SLOT_COUNT) { "MeshCore channel index must be in 0..7" }
    }
}

sealed interface MeshCoreFrame {
    data class Ok(val value: Long?) : MeshCoreFrame

    data class Error(val error: MeshCoreError?) : MeshCoreFrame

    data class ContactsStart(val count: Long) : MeshCoreFrame

    data class Contact(val contact: MeshCoreContact) : MeshCoreFrame

    data class ContactsEnd(val lastModifiedEpochSeconds: Long) : MeshCoreFrame

    data class SelfInfo(val info: MeshCoreSelfInfo) : MeshCoreFrame

    data class DeviceInfo(val info: MeshCoreDeviceInfo) : MeshCoreFrame

    data class Channel(val channel: MeshCoreChannel) : MeshCoreFrame

    data class BatteryAndStorage(val millivolts: Int, val usedKb: Long?, val totalKb: Long?) : MeshCoreFrame

    data class DirectMessage(
        val publicKeyPrefix: ByteArray,
        val path: MeshCorePath,
        val textType: Int,
        val senderTimestamp: Long,
        val signature: ByteArray?,
        val text: String,
        val snrDb: Float?,
    ) : MeshCoreFrame

    data class ChannelMessage(
        val channelIndex: Int,
        val path: MeshCorePath,
        val textType: Int,
        val senderTimestamp: Long,
        val text: String,
        val snrDb: Float?,
    ) : MeshCoreFrame

    data object NoMoreMessages : MeshCoreFrame

    data object MessagesWaiting : MeshCoreFrame

    data class Unknown(val code: Int, val payload: ByteArray) : MeshCoreFrame
}

@Suppress("MagicNumber")
enum class MeshCoreError(val wireValue: Int) {
    UNSUPPORTED_COMMAND(1),
    NOT_FOUND(2),
    TABLE_FULL(3),
    BAD_STATE(4),
    FILE_IO(5),
    ILLEGAL_ARGUMENT(6),
}

private fun Int.toMeshCoreError(): MeshCoreError? = MeshCoreError.entries.firstOrNull { it.wireValue == this }

private class MeshCoreByteReader(private val bytes: ByteArray) {
    private var offset = 0

    val remaining: Int
        get() = bytes.size - offset

    fun readUnsignedByte(): Int {
        requireAvailable(1)
        return bytes[offset++].toInt() and UNSIGNED_BYTE_MASK
    }

    fun readSignedByte(): Int {
        requireAvailable(1)
        return bytes[offset++].toInt()
    }

    fun readUInt16(): Int {
        requireAvailable(UINT16_BYTES)
        return readUnsignedByte() or (readUnsignedByte() shl BYTE_SHIFT)
    }

    fun readUInt32(): Long {
        requireAvailable(UINT32_BYTES)
        return readUnsignedByte().toLong() or
            (readUnsignedByte().toLong() shl BYTE_SHIFT) or
            (readUnsignedByte().toLong() shl UINT32_THIRD_BYTE_SHIFT) or
            (readUnsignedByte().toLong() shl UINT32_FOURTH_BYTE_SHIFT)
    }

    fun readInt32(): Int = readUInt32().toInt()

    fun readBytes(count: Int): ByteArray {
        requireAvailable(count)
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    fun readRemaining(): ByteArray = readBytes(remaining)

    fun skip(count: Int) {
        requireAvailable(count)
        offset += count
    }

    private fun requireAvailable(count: Int) {
        require(remaining >= count) { "Malformed MeshCore frame: need $count bytes, have $remaining" }
    }
}

private fun littleEndianUInt32(value: Long): ByteArray {
    require(value in 0..UINT32_MAX) { "Value must fit an unsigned 32-bit field" }
    return ByteArray(UINT32_BYTES) { index -> (value shr (index * BYTE_SHIFT)).toByte() }
}

private fun littleEndianInt32(value: Int): ByteArray =
    ByteArray(UINT32_BYTES) { index -> (value shr (index * BYTE_SHIFT)).toByte() }

private fun ByteArray.decodeUtf8NullTerminated(): String {
    val end = indexOf(0).takeIf { it >= 0 } ?: size
    return copyOfRange(0, end).decodeToString(throwOnInvalidSequence = false)
}

private fun ByteArray.dropTrailingZeros(): ByteArray {
    val end = indexOfLast { it.toInt() != 0 } + 1
    return copyOf(end)
}

private object Command {
    const val APP_START = 1
    const val SEND_DIRECT_TEXT = 2
    const val SEND_CHANNEL_TEXT = 3
    const val GET_CONTACTS = 4
    const val GET_DEVICE_TIME = 5
    const val SET_DEVICE_TIME = 6
    const val SET_ADVERT_NAME = 8
    const val SYNC_NEXT_MESSAGE = 10
    const val SET_RADIO_PARAMETERS = 11
    const val SET_TX_POWER = 12
    const val SET_ADVERT_LAT_LON = 14
    const val GET_BATTERY_AND_STORAGE = 20
    const val DEVICE_QUERY = 22
    const val GET_CHANNEL = 31
    const val SET_CHANNEL = 32
}

private object Response {
    const val OK = 0
    const val ERROR = 1
    const val CONTACTS_START = 2
    const val CONTACT = 3
    const val END_OF_CONTACTS = 4
    const val SELF_INFO = 5
    const val CONTACT_MESSAGE = 7
    const val CHANNEL_MESSAGE = 8
    const val NO_MORE_MESSAGES = 10
    const val BATTERY_AND_STORAGE = 12
    const val DEVICE_INFO = 13
    const val CONTACT_MESSAGE_V3 = 16
    const val CHANNEL_MESSAGE_V3 = 17
    const val CHANNEL_INFO = 18
}

private object Push {
    const val MESSAGES_WAITING = 0x83
}

private object TextType {
    const val PLAIN: Byte = 0
    const val SIGNED_PLAIN: Byte = 2
}

private const val APP_START_RESERVED_BYTES = 6
private const val PUBLIC_KEY_BYTES = 32
private const val PUBLIC_KEY_PREFIX_BYTES = 6
private const val SIGNATURE_PREFIX_BYTES = 4
private const val MAX_PATH_BYTES = 64
private const val CONTACT_NAME_BYTES = 32
private const val FIRMWARE_BUILD_BYTES = 12
private const val MODEL_NAME_BYTES = 40
private const val FIRMWARE_VERSION_BYTES = 20
private const val DEVICE_INFO_EXTENDED_VERSION = 3
private const val REPEAT_MODE_VERSION = 9
private const val PATH_HASH_MODE_VERSION = 10
private const val MIN_SPREADING_FACTOR = 7
private const val MAX_SPREADING_FACTOR = 12
private const val MIN_CODING_RATE = 5
private const val MAX_CODING_RATE = 8
private const val MAX_ATTEMPT = 255
private const val TELEMETRY_MODE_MASK = 0x03
private const val TELEMETRY_LOCATION_SHIFT = 2
private const val TELEMETRY_ENVIRONMENT_SHIFT = 4
private const val RESERVED_SIGNAL_BYTES = 2
private const val SIGNAL_SCALE = 4f
private const val UINT16_BYTES = 2
private const val UINT32_BYTES = 4
private const val UINT32_MAX = 0xFFFF_FFFFL
private const val BYTE_SHIFT = 8
private const val UINT32_THIRD_BYTE_SHIFT = 16
private const val UINT32_FOURTH_BYTE_SHIFT = 24
private const val UNSIGNED_BYTE_MASK = 0xFF
