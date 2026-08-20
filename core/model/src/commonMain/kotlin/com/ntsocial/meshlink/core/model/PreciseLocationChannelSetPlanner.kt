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
package com.ntsocial.meshlink.core.model

import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannel
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleSettings

/** One active secondary channel that can be selected by a precise-location policy UI. */
data class PreciseLocationChannelOption(
    val index: Int,
    val name: String,
    val channelIdentity: String,
    val positionPrecision: Int,
    val mqttUplinkEnabled: Boolean,
    val requiresKnownPublicWarning: Boolean,
    val firmwarePrecisionLimited: Boolean,
)

/** Pure planner for assigning exact position sharing to exactly one active secondary channel. */
object PreciseLocationChannelSetPlanner {
    const val PRECISE_POSITION_BITS = 32
    private const val DISABLED_POSITION_BITS = 0
    private const val FIRST_SECONDARY_INDEX = 1
    private const val LAST_WELL_KNOWN_PSK_INDEX = 10

    private val meshtasticKnownPublicPsks: Set<ByteString> =
        (0..LAST_WELL_KNOWN_PSK_INDEX)
            .map { index -> Channel(ChannelSettings(psk = byteArrayOf(index.toByte()).toByteString())).psk }
            .toSet()

    private val bundledNtsocialPublicPsk: ByteString by lazy {
        Channel(NtsocialDefaultChannel.channelSet.settings.single()).psk
    }

    /** Lists active secondary slots without filtering known-public channels out of the user's explicit choice. */
    fun activeSecondaryOptions(current: ChannelSet): List<PreciseLocationChannelOption> {
        val loraConfig = current.lora_config ?: Config.LoRaConfig()
        return current.settings.mapIndexedNotNull { index, settings ->
            if (index < FIRST_SECONDARY_INDEX || !settings.isActive()) {
                null
            } else {
                PreciseLocationChannelOption(
                    index = index,
                    name = Channel(settings, loraConfig).name,
                    channelIdentity = requireNotNull(channelIdentity(current, index)),
                    positionPrecision = settings.positionPrecision,
                    mqttUplinkEnabled = settings.uplink_enabled,
                    requiresKnownPublicWarning = settings.requiresKnownPublicWarning(loraConfig),
                    firmwarePrecisionLimited = settings.isFirmwarePublicChannel(loraConfig),
                )
            }
        }
    }

    /**
     * Returns a channel-only write request with [targetIndex] at 32 bits and every other slot at zero bits.
     *
     * The target must be an active secondary. All unrelated channel and module fields are retained. A null LoRa config
     * is intentional: callers must not infer a LoRa write from this policy-only plan.
     */
    fun plan(current: ChannelSet, targetIndex: Int): ChannelSet {
        require(current.hasValidTarget(targetIndex)) { "Target must be an active secondary channel" }
        require(!current.settings[targetIndex].isFirmwarePublicChannel(current.lora_config ?: Config.LoRaConfig())) {
            "Meshtastic public channels cannot retain 32-bit position precision"
        }
        val plannedSettings =
            current.settings.mapIndexed { index, settings ->
                settings.withPositionPrecision(
                    if (index == targetIndex) PRECISE_POSITION_BITS else DISABLED_POSITION_BITS,
                )
            }
        return current.copy(settings = plannedSettings, lora_config = null)
    }

    /** Returns a channel-only write request that disables position sharing on every configured slot. */
    fun disable(current: ChannelSet): ChannelSet = current.copy(
        settings = current.settings.map { settings -> settings.withPositionPrecision(DISABLED_POSITION_BITS) },
        lora_config = null,
    )

    /** True when every configured channel explicitly disables over-the-air position sharing. */
    fun isDisabled(snapshot: ChannelSet): Boolean = snapshot.settings.all { settings ->
        !settings.isActive() ||
            (settings.module_settings != null && settings.positionPrecision == DISABLED_POSITION_BITS)
    }

    /**
     * Stable disclosure identity of an active slot, binding consent to its resolved PSK and MQTT uplink exposure.
     *
     * A same-key edit which enables MQTT must require fresh consent instead of silently resuming precise GPS sharing.
     */
    fun channelIdentity(snapshot: ChannelSet, targetIndex: Int): String? {
        if (!snapshot.hasValidTarget(targetIndex)) return null
        val settings = snapshot.settings[targetIndex]
        val sourceChannelId =
            NtsocialGatewayIdentity.channel(
                channel =
                org.meshtastic.proto.Channel(
                    index = targetIndex,
                    role = org.meshtastic.proto.Channel.Role.SECONDARY,
                    settings = settings,
                ),
                loraConfig = snapshot.lora_config ?: Config.LoRaConfig(),
            )
                .sourceChannelId
        return "$sourceChannelId|mqttUplink=${settings.uplink_enabled}"
    }

    /** True only when one exact slot is p32, every other slot is p0, and optional channel identity still matches. */
    fun matchesPolicy(snapshot: ChannelSet, targetIndex: Int, expectedChannelIdentity: String? = null): Boolean =
        snapshot.hasValidTarget(targetIndex) &&
            snapshot.settings.withIndex().all { (index, settings) ->
                !settings.isActive() ||
                    (
                        settings.module_settings != null &&
                            settings.positionPrecision ==
                            if (index == targetIndex) PRECISE_POSITION_BITS else DISABLED_POSITION_BITS
                        )
            } &&
            (expectedChannelIdentity == null || channelIdentity(snapshot, targetIndex) == expectedChannelIdentity)

    private fun ChannelSet.hasValidTarget(targetIndex: Int): Boolean =
        targetIndex >= FIRST_SECONDARY_INDEX && targetIndex < settings.size && settings[targetIndex].isActive()

    private fun ChannelSettings.withPositionPrecision(positionPrecision: Int): ChannelSettings {
        if (!isActive()) return this
        return copy(
            module_settings = (module_settings ?: ModuleSettings()).copy(position_precision = positionPrecision),
        )
    }

    private val ChannelSettings.positionPrecision: Int
        get() = module_settings?.position_precision ?: DISABLED_POSITION_BITS

    private fun ChannelSettings.isActive(): Boolean = this != ChannelSettings()

    private fun ChannelSettings.requiresKnownPublicWarning(loraConfig: Config.LoRaConfig): Boolean {
        val resolvedPsk = Channel(this, loraConfig).psk
        return isFirmwarePublicChannel(loraConfig) || resolvedPsk == bundledNtsocialPublicPsk
    }

    private fun ChannelSettings.isFirmwarePublicChannel(loraConfig: Config.LoRaConfig): Boolean {
        val resolvedPsk = Channel(this, loraConfig).psk
        return psk.size <= 1 || resolvedPsk in meshtasticKnownPublicPsks
    }
}
