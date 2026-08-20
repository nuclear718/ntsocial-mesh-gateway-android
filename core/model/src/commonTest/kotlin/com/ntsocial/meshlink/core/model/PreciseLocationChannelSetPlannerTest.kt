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
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreciseLocationChannelSetPlannerTest {
    private val lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW)

    @Test
    fun `plan targets slot four and disables position on every other channel`() {
        val current = fiveChannelSet()

        val planned = PreciseLocationChannelSetPlanner.plan(current, targetIndex = 4)

        assertNull(planned.lora_config)
        assertEquals(listOf(0, 0, 0, 0, 32), planned.settings.map { settings -> settings.positionPrecision })
        assertTrue(PreciseLocationChannelSetPlanner.matchesPolicy(planned.copy(lora_config = lora), targetIndex = 4))
    }

    @Test
    fun `plan preserves channel fields and unrelated module settings`() {
        val target =
            channel(
                name = "Private",
                keyByte = 5,
                precision = 13,
                uplink = true,
                downlink = true,
                muted = true,
                id = 1234,
                channelNum = 7,
            )
        val other =
            channel(
                name = "Other",
                keyByte = 6,
                precision = 19,
                uplink = true,
                downlink = false,
                muted = true,
                id = 5678,
                channelNum = 8,
            )
        val current = ChannelSet(settings = listOf(channel("Primary", 1), target, other), lora_config = lora)

        val planned = PreciseLocationChannelSetPlanner.plan(current, targetIndex = 1)

        assertEquals(
            target.copy(module_settings = target.module_settings?.copy(position_precision = 32)),
            planned.settings[1],
        )
        assertEquals(
            other.copy(module_settings = other.module_settings?.copy(position_precision = 0)),
            planned.settings[2],
        )
    }

    @Test
    fun `plan explicitly writes p0 and p32 when configured slots have no module settings`() {
        val primary = channel("Primary", 1).copy(module_settings = null)
        val target = channel("Private", 2).copy(module_settings = null)
        val other = channel("Other", 3).copy(module_settings = null)
        val current = ChannelSet(settings = listOf(primary, target, other), lora_config = lora)

        val planned = PreciseLocationChannelSetPlanner.plan(current, targetIndex = 1)

        assertEquals(0, planned.settings[0].module_settings?.position_precision)
        assertEquals(32, planned.settings[1].module_settings?.position_precision)
        assertEquals(0, planned.settings[2].module_settings?.position_precision)
        assertTrue(PreciseLocationChannelSetPlanner.matchesPolicy(planned, targetIndex = 1))
    }

    @Test
    fun `plan preserves a placeholder hole before the target`() {
        val primary = channel("Primary", 1).copy(module_settings = null)
        val target = channel("Private", 2).copy(module_settings = null)
        val other = channel("Other", 3).copy(module_settings = null)
        val current = ChannelSet(settings = listOf(primary, ChannelSettings(), target, other), lora_config = lora)

        val planned = PreciseLocationChannelSetPlanner.plan(current, targetIndex = 2)

        assertEquals(0, planned.settings[0].module_settings?.position_precision)
        assertEquals(ChannelSettings(), planned.settings[1])
        assertEquals(32, planned.settings[2].module_settings?.position_precision)
        assertEquals(0, planned.settings[3].module_settings?.position_precision)
    }

    @Test
    fun `disable explicitly writes p0 when configured slots have no module settings`() {
        val primary = channel("Primary", 1).copy(module_settings = null)
        val secondary = channel("Secondary", 2).copy(module_settings = null)
        val disabledSlot = ChannelSettings()
        val current = ChannelSet(settings = listOf(primary, secondary, disabledSlot), lora_config = lora)

        val disabled = PreciseLocationChannelSetPlanner.disable(current)

        assertEquals(0, disabled.settings[0].module_settings?.position_precision)
        assertEquals(0, disabled.settings[1].module_settings?.position_precision)
        assertEquals(ChannelSettings(), disabled.settings[2])
    }

    @Test
    fun `disable clears every position precision while preserving all other fields`() {
        val current = fiveChannelSet()

        val disabled = PreciseLocationChannelSetPlanner.disable(current)

        assertNull(disabled.lora_config)
        assertEquals(listOf(0, 0, 0, 0, 0), disabled.settings.map { settings -> settings.positionPrecision })
        current.settings.zip(disabled.settings).forEach { (before, after) ->
            assertEquals(before.copy(module_settings = before.module_settings?.copy(position_precision = 0)), after)
        }
    }

    @Test
    fun `plan rejects primary out of range and inactive targets`() {
        val current =
            ChannelSet(
                settings = listOf(channel("Primary", 1), channel("Secondary", 2), ChannelSettings()),
                lora_config = lora,
            )

        assertFailsWith<IllegalArgumentException> { PreciseLocationChannelSetPlanner.plan(current, targetIndex = 0) }
        assertFailsWith<IllegalArgumentException> { PreciseLocationChannelSetPlanner.plan(current, targetIndex = 3) }
        assertFailsWith<IllegalArgumentException> { PreciseLocationChannelSetPlanner.plan(current, targetIndex = 2) }
    }

    @Test
    fun `policy validation rejects any non-target precision and an imprecise target`() {
        val planned = PreciseLocationChannelSetPlanner.plan(fiveChannelSet(), targetIndex = 4)

        val nonTargetEnabled =
            planned.copy(
                settings =
                planned.settings.mapIndexed { index, settings ->
                    if (index == 2) settings.withPrecision(13) else settings
                },
            )
        val targetImprecise =
            planned.copy(
                settings =
                planned.settings.mapIndexed { index, settings ->
                    if (index == 4) settings.withPrecision(13) else settings
                },
            )

        assertFalse(PreciseLocationChannelSetPlanner.matchesPolicy(nonTargetEnabled, targetIndex = 4))
        assertFalse(PreciseLocationChannelSetPlanner.matchesPolicy(targetImprecise, targetIndex = 4))
        assertFalse(PreciseLocationChannelSetPlanner.matchesPolicy(planned, targetIndex = 0))
    }

    @Test
    fun `policy validation requires explicit p0 on every configured non-target slot`() {
        val planned = PreciseLocationChannelSetPlanner.plan(fiveChannelSet(), targetIndex = 4)
        val absentNonTargetModule =
            planned.copy(
                settings =
                planned.settings.mapIndexed { index, settings ->
                    if (index == 2) settings.copy(module_settings = null) else settings
                },
            )

        assertFalse(PreciseLocationChannelSetPlanner.matchesPolicy(absentNonTargetModule, targetIndex = 4))
    }

    @Test
    fun `policy validation binds consent to the expected channel identity`() {
        val snapshot = PreciseLocationChannelSetPlanner.plan(fiveChannelSet(), targetIndex = 4).copy(lora_config = lora)
        val identity = requireNotNull(PreciseLocationChannelSetPlanner.channelIdentity(snapshot, targetIndex = 4))

        assertTrue(PreciseLocationChannelSetPlanner.matchesPolicy(snapshot, targetIndex = 4, identity))
        assertFalse(PreciseLocationChannelSetPlanner.matchesPolicy(snapshot, targetIndex = 4, "$identity-stale"))
    }

    @Test
    fun `policy identity changes when the selected channel enables MQTT uplink`() {
        val snapshot = PreciseLocationChannelSetPlanner.plan(fiveChannelSet(), targetIndex = 4).copy(lora_config = lora)
        val identity = requireNotNull(PreciseLocationChannelSetPlanner.channelIdentity(snapshot, targetIndex = 4))
        val mqttEnabled =
            snapshot.copy(
                settings =
                snapshot.settings.mapIndexed { index, settings ->
                    if (index == 4) settings.copy(uplink_enabled = true) else settings
                },
            )

        assertFalse(PreciseLocationChannelSetPlanner.matchesPolicy(mqttEnabled, targetIndex = 4, identity))
        assertFalse(identity == PreciseLocationChannelSetPlanner.channelIdentity(mqttEnabled, targetIndex = 4))
    }

    @Test
    fun `firmware limited public aliases cannot be planned for p32`() {
        val publicSecondary =
            ChannelSettings(
                name = "LongFast",
                psk = byteArrayOf(1).toByteString(),
                module_settings = ModuleSettings(position_precision = 13),
            )
        val current = ChannelSet(settings = listOf(channel("Primary", 7), publicSecondary), lora_config = lora)

        val option = PreciseLocationChannelSetPlanner.activeSecondaryOptions(current).single()

        assertTrue(option.requiresKnownPublicWarning)
        assertTrue(option.firmwarePrecisionLimited)
        assertFailsWith<IllegalArgumentException> { PreciseLocationChannelSetPlanner.plan(current, targetIndex = 1) }
    }

    @Test
    fun `active options include slot four canonical NTsocial with known public warning`() {
        val ntsocial = NtsocialDefaultChannel.channelSet.settings.single()
        val current =
            ChannelSet(
                settings =
                listOf(
                    channel("Primary", 1),
                    channel("Private A", 2, precision = 13),
                    ChannelSettings(),
                    channel("Private B", 3, precision = 32, uplink = true),
                    ntsocial,
                ),
                lora_config = lora,
            )

        val options = PreciseLocationChannelSetPlanner.activeSecondaryOptions(current)

        assertEquals(listOf(1, 3, 4), options.map(PreciseLocationChannelOption::index))
        assertEquals("Private A", options[0].name)
        assertEquals(13, options[0].positionPrecision)
        assertFalse(options[0].mqttUplinkEnabled)
        assertFalse(options[0].requiresKnownPublicWarning)
        assertEquals(
            PreciseLocationChannelSetPlanner.channelIdentity(current, targetIndex = 1),
            options[0].channelIdentity,
        )
        assertEquals(32, options[1].positionPrecision)
        assertTrue(options[1].mqttUplinkEnabled)
        assertEquals(NtsocialDefaultChannel.CHANNEL_NAME, options[2].name)
        assertTrue(options[2].requiresKnownPublicWarning)
        assertFalse(options[2].firmwarePrecisionLimited)
        assertEquals(
            PreciseLocationChannelSetPlanner.channelIdentity(current, targetIndex = 4),
            options[2].channelIdentity,
        )
    }

    private fun fiveChannelSet(): ChannelSet = ChannelSet(
        settings =
        listOf(
            channel("Primary", 1, precision = 13),
            channel("Secondary 1", 2, precision = 10),
            channel("Secondary 2", 3, precision = 0),
            channel("Secondary 3", 4, precision = 19),
            channel("Private slot 4", 5, precision = 13),
        ),
        lora_config = lora,
    )

    @Suppress("LongParameterList")
    private fun channel(
        name: String,
        keyByte: Int,
        precision: Int = 0,
        uplink: Boolean = false,
        downlink: Boolean = false,
        muted: Boolean = false,
        id: Int = 0,
        channelNum: Int = 0,
    ): ChannelSettings = ChannelSettings(
        channel_num = channelNum,
        psk = keyByte.toString(16).padStart(2, '0').repeat(16).decodeHex(),
        name = name,
        id = id,
        uplink_enabled = uplink,
        downlink_enabled = downlink,
        module_settings = ModuleSettings(position_precision = precision, is_muted = muted),
    )

    private fun ChannelSettings.withPrecision(precision: Int): ChannelSettings =
        copy(module_settings = (module_settings ?: ModuleSettings()).copy(position_precision = precision))

    private val ChannelSettings.positionPrecision: Int
        get() = module_settings?.position_precision ?: 0
}
