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
package com.ntsocial.meshlink.core.ui.util

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.model.util.getChannelUrl
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.testing.FakeRadioInterfaceService
import com.ntsocial.meshlink.core.ui.component.MeshtasticImportFAB
import com.ntsocial.meshlink.core.ui.qr.ScannedQrCodeDialog
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.compose.scope.UnboundKoinScope
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class, KoinDelicateAPI::class, KoinExperimentalAPI::class)
class EndpointImportUiTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scannerPreviewAndConfirmationBelongToEachOfFourNonRootScopes() = runComposeUiTest {
        val application = koinApplication {}
        val owners =
            (0..3).map { index ->
                application.koin.createScope("endpoint-$index", named("endpoint")).apply {
                    declare<RadioInterfaceService>(
                        FakeRadioInterfaceService().apply {
                            setDeviceAddress("radio$index")
                            onConnect()
                            markCurrentSessionConfigured(radioSessionState.value.epoch)
                        },
                    )
                }
            }
        val confirmed = mutableMapOf<Int, ChannelSet>()
        var selected by mutableStateOf(0)
        var scanResult: (String?) -> Unit = {}
        setContent {
            val owner = selected
            UnboundKoinScope(owners[owner]) {
                val imports = rememberEndpointImportState()
                key(imports) {
                    CompositionLocalProvider(
                        LocalBarcodeScannerSupported provides true,
                        LocalBarcodeScannerProvider provides
                            { onResult ->
                                object : BarcodeScanner {
                                    override fun startScan() {
                                        scanResult = onResult
                                    }
                                }
                            },
                    ) {
                        AppTheme {
                            MeshtasticImportFAB(
                                onImport = { imports.handleUri(CommonUri.parse(it)) { error("Valid channel URL") } },
                                testTag = "import",
                            )
                            imports.channelSet?.let { incoming ->
                                ScannedQrCodeDialog(
                                    channels =
                                    ChannelSet(
                                        settings = listOf(ChannelSettings(name = "Primary$owner")),
                                        lora_config = Config.LoRaConfig(),
                                    ),
                                    incoming = incoming,
                                    targetLabel = "Radio$owner",
                                    onDismiss = imports::clearChannelSet,
                                    onConfirm = { confirmed[owner] = it },
                                )
                            }
                        }
                    }
                }
            }
        }
        owners.indices.forEach { index ->
            assertFalse(owners[index].isRoot)
            runOnIdle { selected = index }
            onNodeWithTag("import").performClick()
            onNodeWithTag("qr_import").performClick()
            runOnIdle { scanResult(channelUrl("QR$index")) }
            onNodeWithText("Radio$index", substring = true).assertIsDisplayed()
            assertEquals(index, confirmed.size) // Decoding and preview must not apply anything.
            onNodeWithText("Accept").assertIsDisplayed().performClick()
            assertEquals(listOf("Primary$index", "QR$index"), confirmed[index]?.settings?.map { it.name })
        }
        // A late result from a screen that was switched away from cannot populate the new endpoint's preview.
        onNodeWithTag("import").performClick()
        onNodeWithTag("qr_import").performClick()
        val retiredResult = scanResult
        runOnIdle { selected = 0 }
        runOnIdle { retiredResult(channelUrl("Retired")) }
        onNodeWithText("Accept").assertDoesNotExist()
        assertEquals(4, confirmed.size)
    }

    private fun channelUrl(name: String) =
        ChannelSet(settings = listOf(ChannelSettings(name = name))).getChannelUrl(shouldAdd = true).toString()
}
