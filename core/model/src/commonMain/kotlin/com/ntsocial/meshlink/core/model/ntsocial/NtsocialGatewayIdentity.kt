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
package com.ntsocial.meshlink.core.model.ntsocial

import com.ntsocial.meshlink.core.model.DataPacket
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.PortNum
import com.ntsocial.meshlink.core.model.Channel as ModelChannel

/** Stable, opaque identity exported for one enabled Meshtastic channel. */
data class NtsocialGatewayChannelIdentity(
    val sourceChannelId: String,
    val configuredName: String,
    val displayName: String,
    val securityClass: String,
)

/** Stable identity captured with a native broadcast text row when it is first persisted. */
data class NtsocialGatewayMessageIdentity(val sourceChannelId: String, val sourceMessageId: String)

/** One durable native-text change backed by the packet database's monotonic local row id. */
data class NtsocialGatewayMessageChange(
    val changeSeq: Long,
    val identity: NtsocialGatewayMessageIdentity?,
    val packet: DataPacket,
    val receivedAtMillis: Long,
)

/** Atomic history cursor domain exposed by Gateway v2 status. */
data class NtsocialGatewayHistoryState(val historyEpoch: String, val messageChangeSeq: Long)

/** Install-local key used only to blind legacy channel identity material. */
interface NtsocialGatewayIdentityKeyProvider {
    val legacyChannelHmacKey: ByteString
}

/**
 * Domain-separated gateway identities.
 *
 * Zero-ID CLEAR/WELL_KNOWN settings use public material so separate installs converge. Only zero-ID CUSTOM settings
 * require an install-local HMAC key, preventing exported IDs from becoming offline PSK dictionary oracles.
 */
object NtsocialGatewayIdentity {
    private const val CHANNEL_PREFIX = "meshtastic:"
    private const val SOURCE_MESSAGE_ID_HEX_LENGTH = 32

    fun channel(
        channel: Channel,
        loraConfig: Config.LoRaConfig = Config.LoRaConfig(),
        legacyChannelHmacKey: ByteString? = null,
    ): NtsocialGatewayChannelIdentity {
        val settings = channel.settings ?: ChannelSettings()
        val model = ModelChannel(settings, loraConfig)
        val securityClass =
            when {
                settings.psk.size == 0 || (settings.psk.size == 1 && settings.psk[0].toInt() == 0) -> "CLEAR"
                settings.psk.size == 1 -> "WELL_KNOWN"
                else -> "CUSTOM"
            }
        val stableMaterial =
            when {
                settings.id != 0 ->
                    digest("ntsocial-gateway-channel-id-v2".encodeUtf8(), settings.id.toUInt().toString().encodeUtf8())

                securityClass != "CUSTOM" ->
                    digest(
                        "ntsocial-gateway-channel-public-v2".encodeUtf8(),
                        canonicalPublicName(model.name).encodeUtf8(),
                        model.psk,
                    )

                else -> {
                    val key =
                        requireNotNull(legacyChannelHmacKey?.takeIf { it.size >= MIN_LEGACY_HMAC_KEY_BYTES }) {
                            "Custom legacy channel identity requires an install-local HMAC key"
                        }
                    framed(
                        "ntsocial-gateway-channel-custom-v2".encodeUtf8(),
                        canonicalPublicName(model.name).encodeUtf8(),
                        model.psk,
                    )
                        .hmacSha256(key)
                }
            }
        return NtsocialGatewayChannelIdentity(
            sourceChannelId = CHANNEL_PREFIX + stableMaterial.hex(),
            configuredName = settings.name,
            displayName = model.name,
            securityClass = securityClass,
        )
    }

    fun nativeBroadcastText(
        channel: NtsocialGatewayChannelIdentity,
        packet: DataPacket,
    ): NtsocialGatewayMessageIdentity? {
        val payload =
            packet.bytes?.takeIf {
                packet.dataType == PortNum.TEXT_MESSAGE_APP.value &&
                    packet.to == DataPacket.ID_BROADCAST &&
                    !packet.from.isNullOrBlank() &&
                    packet.from != DataPacket.ID_LOCAL
            }
        return payload?.let {
            val sourceMessageId =
                digest(
                    "ntsocial-gateway-message-v2".encodeUtf8(),
                    channel.sourceChannelId.encodeUtf8(),
                    packet.from.orEmpty().encodeUtf8(),
                    packet.id.toUInt().toString().encodeUtf8(),
                    packet.dataType.toString().encodeUtf8(),
                    payload.sha256(),
                )
            NtsocialGatewayMessageIdentity(
                sourceChannelId = channel.sourceChannelId,
                sourceMessageId = sourceMessageId.hex().take(SOURCE_MESSAGE_ID_HEX_LENGTH).uppercase(),
            )
        }
    }

    fun nativeBroadcastText(
        settings: ChannelSettings,
        loraConfig: Config.LoRaConfig,
        channelIndex: Int,
        packet: DataPacket,
        legacyChannelHmacKey: ByteString? = null,
    ): NtsocialGatewayMessageIdentity? = nativeBroadcastText(
        channel =
        channel(
            Channel(
                index = channelIndex,
                role = if (channelIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
                settings = settings,
            ),
            loraConfig,
            legacyChannelHmacKey,
        ),
        packet = packet,
    )

    private fun digest(vararg parts: ByteString): ByteString = framed(*parts).sha256()

    /**
     * [String.lowercase] is locale-independent in common Kotlin. Resolving [ModelChannel.name] first keeps an empty
     * default-channel name interoperable with an explicitly configured display name such as `LongFast`.
     */
    private fun canonicalPublicName(name: String): String = name.trim().lowercase()

    private fun framed(vararg parts: ByteString): ByteString {
        val buffer = Buffer()
        parts.forEach { part ->
            buffer.writeInt(part.size)
            buffer.write(part)
        }
        return buffer.readByteString()
    }

    private const val MIN_LEGACY_HMAC_KEY_BYTES = 32
}
