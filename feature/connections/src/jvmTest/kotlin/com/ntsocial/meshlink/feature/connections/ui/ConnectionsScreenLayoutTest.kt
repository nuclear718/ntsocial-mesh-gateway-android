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
package com.ntsocial.meshlink.feature.connections.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.testing.FakeBleDevice
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import com.ntsocial.meshlink.feature.connections.ui.components.BluetoothDeviceList
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ConnectionsScreenLayoutTest {
    @Test
    fun compactViewportCanScrollPastFleetAndStatusToLastBluetoothDevice() = runComposeUiTest {
        var selected: String? = null
        setContent { AppTheme { TestConnections(Modifier.size(360.dp, 480.dp)) { selected = it.fullAddress } } }
        onNodeWithText("Fleet cards").assertIsDisplayed()
        onNodeWithTag("devices").performTouchInput { swipeUp() }
        onNodeWithTag("devices").performScrollToNode(hasText("Radio 11"))
        onNodeWithText("Radio 11").assertIsDisplayed().performClick()
        assertEquals("xradio11", selected)
        onNodeWithTag("devices").performScrollToNode(hasText("Fleet cards"))
        onNodeWithText("Fleet cards").assertIsDisplayed()
    }

    @Test
    fun wideViewportKeepsBluetoothListReachableBesideTallFleetContent() = runComposeUiTest {
        setContent { AppTheme { TestConnections(Modifier.size(840.dp, 480.dp)) {} } }
        onNodeWithText("Fleet cards").assertIsDisplayed()
        onNodeWithText("Radio 0").assertIsDisplayed()
        onNodeWithTag("devices").performScrollToNode(hasText("Radio 11"))
        onNodeWithText("Radio 11").assertIsDisplayed()
        onNodeWithText("Fleet cards").assertIsDisplayed()
    }
}

@Composable
private fun TestConnections(modifier: Modifier, onSelect: (DeviceListEntry.Ble) -> Unit) {
    ConnectionsScreenLayout(
        modifier = modifier,
        fleetContent = { Box(Modifier.height(560.dp)) { Text("Fleet cards") } },
        statusContent = { Box(Modifier.height(240.dp)) { Text("Connected radio") } },
        deviceListContent = { leadingItems ->
            BluetoothDeviceList(
                modifier = Modifier.testTag("devices"),
                connectionState = ConnectionState.Connected,
                selectedDevice = "xcurrent",
                bleDevices = (0..11).map { DeviceListEntry.Ble(FakeBleDevice("radio$it", "Radio $it")) },
                isBleScanning = false,
                onSelectDevice = onSelect,
                onToggleBleScan = {},
                leadingItems = leadingItems,
            )
        },
    )
}
