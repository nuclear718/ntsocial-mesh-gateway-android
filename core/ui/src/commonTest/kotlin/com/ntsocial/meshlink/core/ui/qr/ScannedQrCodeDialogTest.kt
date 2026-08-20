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
package com.ntsocial.meshlink.core.ui.qr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ScannedQrCodeDialogTest {

    @Test
    fun `add-only Replace explains the wait before Accept dismisses after admission`() = runComposeUiTest {
        val primary = channel("Primary", 1)
        val oldSecondary = channel("Old", 2)
        val keptSecondary = channel("Keep", 3)
        val newSecondary = channel("New", 4)
        val events = mutableListOf<String>()
        var confirmed: ChannelSet? = null
        var showDialog by mutableStateOf(true)

        setContent {
            AppTheme {
                if (showDialog) {
                    ScannedQrCodeDialog(
                        channels =
                        ChannelSet(
                            settings = listOf(primary, oldSecondary, keptSecondary),
                            lora_config = CURRENT_LORA,
                        ),
                        incoming = ChannelSet(settings = listOf(newSecondary), lora_config = null),
                        onDismiss = {
                            events += "dismiss"
                            showDialog = false
                        },
                        maxChannels = 3,
                        onConfirm = {
                            events += "confirm"
                            confirmed = it
                        },
                    )
                }
            }
        }

        onNodeWithText("Replace").assertIsEnabled().performClick()
        onNodeWithText(PARTIAL_REPLACE_DESCRIPTION).assertIsDisplayed()

        val selections = onAllNodes(isToggleable())
        selections.assertCountEquals(4)
        selections[0].assertIsNotEnabled().assertIsOn()
        selections[1].assertIsEnabled().assertIsOn().performClick()
        selections[2].assertIsEnabled().assertIsOn()
        selections[3].assertIsEnabled().assertIsOff().performClick()

        onNodeWithText(ACCEPT_NOTICE).assertIsDisplayed()
        onNodeWithText("Accept").performClick()
        waitForIdle()

        assertEquals(listOf("confirm", "dismiss"), events)
        assertEquals(listOf(primary, newSecondary, keptSecondary), assertNotNull(confirmed).settings)
        assertNull(confirmed.lora_config)
        onNodeWithText("Accept").assertDoesNotExist()
    }

    @Test
    fun `add-only QR defaults to Add and preserves every existing channel`() = runComposeUiTest {
        val primary = channel("Primary", 1)
        val oldSecondary = channel("Old", 2)
        val newSecondary = channel("New", 3)
        var confirmed: ChannelSet? = null

        setContent {
            AppTheme {
                ScannedQrCodeDialog(
                    channels = ChannelSet(settings = listOf(primary, oldSecondary), lora_config = CURRENT_LORA),
                    incoming = ChannelSet(settings = listOf(newSecondary), lora_config = null),
                    onDismiss = {},
                    maxChannels = 3,
                    onConfirm = { confirmed = it },
                )
            }
        }

        val selections = onAllNodes(isToggleable())
        selections.assertCountEquals(3)
        selections[0].assertIsNotEnabled().assertIsOn()
        selections[1].assertIsNotEnabled().assertIsOn()
        selections[2].assertIsEnabled().assertIsOn()

        onNodeWithText("Accept").performClick()

        assertEquals(listOf(primary, oldSecondary, newSecondary), assertNotNull(confirmed).settings)
        assertNull(confirmed.lora_config)
    }

    @Test
    fun `partial replacement cannot compact a retained secondary into an unfilled slot`() = runComposeUiTest {
        val primary = channel("Primary", 1)
        val first = channel("First", 2)
        val second = channel("Second", 3)
        val kept = channel("Keep", 4)
        val incoming = channel("New", 5)

        setContent {
            AppTheme {
                ScannedQrCodeDialog(
                    channels = ChannelSet(settings = listOf(primary, first, second, kept), lora_config = CURRENT_LORA),
                    incoming = ChannelSet(settings = listOf(incoming), lora_config = null),
                    onDismiss = {},
                    maxChannels = 4,
                    onConfirm = {},
                )
            }
        }

        onNodeWithText("Replace").performClick()
        val selections = onAllNodes(isToggleable())
        selections[1].performClick()
        selections[2].performClick()
        selections[4].performClick()
        onNodeWithText("Accept").assertIsNotEnabled()

        selections[2].performClick()
        onNodeWithText("Accept").assertIsEnabled()
    }

    @Test
    fun `partial replacement fills an existing disabled slot before a retained secondary`() = runComposeUiTest {
        val primary = channel("Primary", 1)
        val kept = channel("Keep", 2)
        val incoming = channel("New", 3)
        var confirmed: ChannelSet? = null

        setContent {
            AppTheme {
                ScannedQrCodeDialog(
                    channels =
                    ChannelSet(settings = listOf(primary, ChannelSettings(), kept), lora_config = CURRENT_LORA),
                    incoming = ChannelSet(settings = listOf(incoming), lora_config = null),
                    onDismiss = {},
                    maxChannels = 3,
                    onConfirm = { confirmed = it },
                )
            }
        }

        onNodeWithText("Replace").performClick()
        val selections = onAllNodes(isToggleable())
        selections.assertCountEquals(4)
        selections[1].assertIsNotEnabled().assertIsOff()
        selections[3].assertIsEnabled().assertIsOff()
        onNodeWithText("Accept").assertIsNotEnabled()

        selections[3].performClick()
        onNodeWithText("Accept").assertIsEnabled().performClick()
        assertEquals(listOf(primary, incoming, kept), assertNotNull(confirmed).settings)
    }

    @Test
    fun `dialog applies the latest radio capacity`() = runComposeUiTest {
        val primary = channel("Primary", 1)
        val oldSecondary = channel("Old", 2)
        val newSecondary = channel("New", 3)
        var maxChannels by mutableStateOf(3)
        var confirmed: ChannelSet? = null

        setContent {
            AppTheme {
                ScannedQrCodeDialog(
                    channels = ChannelSet(settings = listOf(primary, oldSecondary), lora_config = CURRENT_LORA),
                    incoming = ChannelSet(settings = listOf(newSecondary), lora_config = null),
                    onDismiss = {},
                    maxChannels = maxChannels,
                    onConfirm = { confirmed = it },
                )
            }
        }

        runOnIdle { maxChannels = 2 }
        waitForIdle()
        onAllNodes(isToggleable())[2].assertIsEnabled().assertIsOff()
        onNodeWithText("Accept").performClick()

        assertEquals(listOf(primary, oldSecondary), assertNotNull(confirmed).settings)
        assertNull(confirmed.lora_config)
    }

    @Test
    fun `channel-only actions wait for the complete current radio state`() = runComposeUiTest {
        val primary = channel("Primary", 1)
        var channels by mutableStateOf(ChannelSet())
        setContent {
            AppTheme {
                ScannedQrCodeDialog(
                    channels = channels,
                    incoming = ChannelSet(settings = listOf(channel("New", 1)), lora_config = null),
                    onDismiss = {},
                    maxChannels = 2,
                    onConfirm = {},
                )
            }
        }

        onNodeWithText("Replace").assertIsNotEnabled()
        onNodeWithText("Accept").assertIsNotEnabled()

        runOnIdle { channels = ChannelSet(settings = listOf(primary), lora_config = null) }
        onNodeWithText("Replace").assertIsNotEnabled()
        onNodeWithText("Accept").assertIsNotEnabled()

        runOnIdle { channels = ChannelSet(settings = listOf(primary), lora_config = CURRENT_LORA) }
        onNodeWithText("Replace").assertIsEnabled()
        onNodeWithText("Accept").assertIsEnabled()
    }

    @Test
    fun `full configuration Replace keeps complete replacement semantics`() = runComposeUiTest {
        val oldPrimary = channel("Old primary", 1)
        val oldSecondary = channel("Old secondary", 2)
        val newPrimary = channel("New primary", 3)
        val newSecondary = channel("New secondary", 4)
        val incomingLora =
            Config.LoRaConfig(
                region = Config.LoRaConfig.RegionCode.US,
                use_preset = true,
                modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST,
            )
        var confirmed: ChannelSet? = null

        setContent {
            AppTheme {
                ScannedQrCodeDialog(
                    channels = ChannelSet(settings = listOf(oldPrimary, oldSecondary), lora_config = CURRENT_LORA),
                    incoming = ChannelSet(settings = listOf(newPrimary, newSecondary), lora_config = incomingLora),
                    onDismiss = {},
                    maxChannels = 2,
                    onConfirm = { confirmed = it },
                )
            }
        }

        onNodeWithText(FULL_REPLACE_DESCRIPTION).assertIsDisplayed()
        onNodeWithText("Accept").performClick()

        val applied = assertNotNull(confirmed)
        assertEquals(listOf(newPrimary, newSecondary), applied.settings)
        assertEquals(Config.LoRaConfig.RegionCode.US, applied.lora_config?.region)
        assertEquals(CURRENT_LORA.config_ok_to_mqtt, applied.lora_config?.config_ok_to_mqtt)
        assertEquals(CURRENT_LORA.tx_power, applied.lora_config?.tx_power)
    }

    private fun channel(name: String, keyByte: Int): ChannelSettings =
        ChannelSettings(name = name, psk = byteArrayOf(keyByte.toByte()).toByteString())

    private companion object {
        val CURRENT_LORA =
            Config.LoRaConfig(
                region = Config.LoRaConfig.RegionCode.TW,
                use_preset = true,
                modem_preset = Config.LoRaConfig.ModemPreset.MEDIUM_FAST,
                config_ok_to_mqtt = true,
                tx_power = 17,
            )

        const val PARTIAL_REPLACE_DESCRIPTION =
            "Select the existing secondary channels to keep and the QR code channels to add. " +
                "Uncheck a secondary channel to free a slot. Your primary channel and current radio settings will " +
                "be preserved."

        const val FULL_REPLACE_DESCRIPTION =
            "This QR code contains a complete configuration. This will REPLACE your existing channels and radio " +
                "settings. All existing channels will be removed."

        const val ACCEPT_NOTICE =
            "After you tap Accept, the node normally restarts. Reconnecting and verification take about 30 seconds. " +
                "Please wait; the channel list may not update immediately when you return. The settings will " +
                "continue in the background."
    }
}
