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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.bluetooth_pairing_continue
import com.ntsocial.meshlink.core.resources.bluetooth_pairing_instructions
import com.ntsocial.meshlink.core.resources.bluetooth_pairing_title
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.connections
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_butterfly
import com.ntsocial.meshlink.core.resources.no_device_selected
import com.ntsocial.meshlink.core.resources.set_your_region
import com.ntsocial.meshlink.core.resources.unknown_device
import com.ntsocial.meshlink.core.ui.component.AdaptiveTwoPane
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.component.MainAppBar
import com.ntsocial.meshlink.core.ui.icon.Language
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.NoDevice
import com.ntsocial.meshlink.core.ui.viewmodel.ConnectionStatus
import com.ntsocial.meshlink.core.ui.viewmodel.ConnectionsViewModel
import com.ntsocial.meshlink.feature.connections.MOCK_DEVICE_PREFIX
import com.ntsocial.meshlink.feature.connections.NO_DEVICE_SELECTED
import com.ntsocial.meshlink.feature.connections.ScannerViewModel
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import com.ntsocial.meshlink.feature.connections.ui.components.BluetoothDeviceList
import com.ntsocial.meshlink.feature.connections.ui.components.ConnectingDeviceInfo
import com.ntsocial.meshlink.feature.connections.ui.components.CurrentlyConnectedInfo
import com.ntsocial.meshlink.feature.settings.navigation.ConfigRoute
import com.ntsocial.meshlink.feature.settings.navigation.getNavRouteFrom
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.radio.component.PacketResponseStateDialog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.ExperimentalUuidApi

/**
 * Fixed minimum height for the "connected device" card at the top of the Connections screen. Shared across the three UI
 * states (NO_DEVICE, CONNECTING, CONNECTED_WITH_NODE) so the card never collapses or jumps size between state
 * transitions. Sized to comfortably fit the CONNECTED state (battery/RSSI row + node row + disconnect button).
 */
private val CardMinHeight = 100.dp
private val NtsocialConnectionBlue = Color(0xFF3DA8FF)

/** Simple Bluetooth-first screen for managing the radio connection and displaying its current status. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ModifierMissing", "ComposableParamOrder")
@Composable
fun ConnectionsScreen(
    connectionsViewModel: ConnectionsViewModel = koinViewModel(),
    scanModel: ScannerViewModel = koinViewModel(),
    radioConfigViewModel: RadioConfigViewModel = koinViewModel(),
    onClickNodeChip: (Int) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit,
) {
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val connectionProgress by scanModel.connectionProgressText.collectAsStateWithLifecycle()
    val bluetoothPairingGuidance by scanModel.bluetoothPairingGuidance.collectAsStateWithLifecycle()
    val connectionStatus by connectionsViewModel.connectionStatus.collectAsStateWithLifecycle()
    val connectionState by connectionsViewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by connectionsViewModel.ourNodeForDisplay.collectAsStateWithLifecycle()
    val regionUnset by connectionsViewModel.regionUnset.collectAsStateWithLifecycle()

    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()
    val persistedDeviceName by scanModel.persistedDeviceName.collectAsStateWithLifecycle()

    val bleDevices by scanModel.bleDevicesForUi.collectAsStateWithLifecycle()
    val isBleScanning by scanModel.isBleScanning.collectAsStateWithLifecycle()

    val bleAutoScan by scanModel.bleAutoScan.collectAsStateWithLifecycle()

    bluetoothPairingGuidance?.let { guidance ->
        AlertDialog(
            onDismissRequest = scanModel::dismissBluetoothPairingGuidance,
            title = { Text(stringResource(Res.string.bluetooth_pairing_title, guidance.deviceName)) },
            text = { Text(stringResource(Res.string.bluetooth_pairing_instructions)) },
            confirmButton = {
                TextButton(onClick = scanModel::continueBluetoothPairing) {
                    Text(stringResource(Res.string.bluetooth_pairing_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = scanModel::dismissBluetoothPairingGuidance) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    var isScreenResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        isScreenResumed = true
        onPauseOrDispose {
            isScreenResumed = false
            scanModel.stopBleScan()
        }
    }
    // Persisted auto-scan means one bounded sweep when this screen is resumed while disconnected. A connected user can
    // still explicitly scan for another fleet endpoint with the manual action.
    LaunchedEffect(bleAutoScan, isScreenResumed, connectionState) {
        if (
            isScreenResumed &&
            bleAutoScan &&
            connectionState !is ConnectionState.Connected &&
            !scanModel.isBleScanning.value
        ) {
            scanModel.startBleScan()
        }
    }
    LaunchedEffect(connectionState) { if (connectionState is ConnectionState.Connected) scanModel.stopBleScan() }

    /* Animate waiting for the configurations */
    var isWaiting by remember { mutableStateOf(false) }
    if (isWaiting) {
        PacketResponseStateDialog(
            state = radioConfigState.responseState,
            onDismiss = {
                isWaiting = false
                radioConfigViewModel.clearPacketResponse()
            },
            onComplete = {
                getNavRouteFrom(radioConfigState.route)?.let { route ->
                    isWaiting = false
                    radioConfigViewModel.clearPacketResponse()
                    if (route == SettingsRoute.LoRa) {
                        onConfigNavigate(SettingsRoute.LoRa)
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.connections),
                ourNode = ourNode,
                showNodeChip = ourNode != null && connectionState is ConnectionState.Connected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                RadioFleetPanel()

                AdaptiveTwoPane(
                    modifier = Modifier.weight(1f),
                    first = {
                        val uiState =
                            when {
                                connectionState is ConnectionState.Connected && ourNode != null ->
                                    ConnectionUiState.CONNECTED_WITH_NODE

                                connectionState is ConnectionState.Connected ||
                                    connectionState == ConnectionState.Connecting ||
                                    selectedDevice != NO_DEVICE_SELECTED -> ConnectionUiState.CONNECTING

                                else -> ConnectionUiState.NO_DEVICE
                            }

                        // ── Connected Device slot ──
                        // A single Card shell hosts all three states. `animateContentSize` smooths any
                        // height changes, while `heightIn(min = CardMinHeight)` reserves a stable floor so
                        // the card never collapses between states.
                        Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            AnimatedContent(
                                targetState = uiState,
                                label = "connection_state",
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { state ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = CardMinHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (state) {
                                        ConnectionUiState.CONNECTED_WITH_NODE ->
                                            ConnectedDeviceContent(
                                                ourNode = ourNode,
                                                selectedDevice = selectedDevice,
                                                bleDevices = bleDevices,
                                                onNavigateToNodeDetails = onNavigateToNodeDetails,
                                                onClickDisconnect = { scanModel.disconnect() },
                                            )

                                        ConnectionUiState.CONNECTING ->
                                            ConnectingDeviceContent(
                                                selectedDevice = selectedDevice,
                                                persistedDeviceName = persistedDeviceName,
                                                bleDevices = bleDevices,
                                                connectionStatus = connectionStatus,
                                                connectionProgress = connectionProgress,
                                                onClickDisconnect = { scanModel.disconnect() },
                                            )

                                        else -> NoDeviceContent()
                                    }
                                }
                            }
                        }

                        // Region warning sits outside the animated card so it does not affect the
                        // CONNECTED ↔ CONNECTING ↔ NO_DEVICE size transition.
                        if (
                            uiState == ConnectionUiState.CONNECTED_WITH_NODE &&
                            regionUnset &&
                            selectedDevice != MOCK_DEVICE_PREFIX
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    leadingIcon = MeshtasticIcons.Language,
                                    text = stringResource(Res.string.set_your_region),
                                    onClick = {
                                        isWaiting = true
                                        radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                                    },
                                )
                            }
                        }
                    },
                    second = {
                        // Bluetooth is the only connection method exposed in the first-release UI.
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            BluetoothDeviceList(
                                connectionState = connectionState,
                                selectedDevice = selectedDevice,
                                bleDevices = bleDevices,
                                isBleScanning = isBleScanning,
                                onSelectDevice = { scanModel.onSelected(it) },
                                onToggleBleScan = { scanModel.toggleBleScan() },
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Body for the CONNECTED state — sits inside the shared outer Card in [ConnectionsScreen]. */
@Composable
private fun ConnectedDeviceContent(
    ourNode: com.ntsocial.meshlink.core.model.Node?,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry.Ble>,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
) {
    ourNode?.let { node ->
        CurrentlyConnectedInfo(
            node = node,
            bleDevice = bleDevices.find { it.fullAddress == selectedDevice },
            onNavigateToNodeDetails = onNavigateToNodeDetails,
            onClickDisconnect = onClickDisconnect,
        )
    }
}

/** Body for the CONNECTING state — sits inside the shared outer Card in [ConnectionsScreen]. */
@Composable
private fun ConnectingDeviceContent(
    selectedDevice: String,
    persistedDeviceName: String?,
    bleDevices: List<DeviceListEntry.Ble>,
    connectionStatus: ConnectionStatus,
    connectionProgress: String?,
    onClickDisconnect: () -> Unit,
) {
    val selectedEntry = bleDevices.find { it.fullAddress == selectedDevice }

    // Use the entry name if found in scan lists, otherwise fall back to the persisted name
    // from the last successful selection, and only show "Unknown Device" as a last resort.
    val name = selectedEntry?.name ?: persistedDeviceName ?: stringResource(Res.string.unknown_device)
    val address = selectedEntry?.address ?: selectedDevice

    ConnectingDeviceInfo(
        deviceName = name,
        deviceAddress = address,
        connectionStatus = connectionStatus,
        connectionProgress = connectionProgress,
        onClickDisconnect = onClickDisconnect,
    )
}

/** Body for the NO_DEVICE state — sits inside the shared outer Card in [ConnectionsScreen]. */
@Composable
private fun NoDeviceContent() {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = CardMinHeight)) {
        Image(
            painter = painterResource(Res.drawable.img_ntsocial_background_butterfly),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.42f)))
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = MeshtasticIcons.NoDevice,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = NtsocialConnectionBlue,
            )
            Text(
                text = stringResource(Res.string.no_device_selected),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.86f),
            )
        }
    }
}

/** Visual state for the connection screen's [AnimatedContent] transition between the three card body variants. */
private enum class ConnectionUiState {
    /** No device is selected. */
    NO_DEVICE,

    /** A device is selected or we are actively connecting. */
    CONNECTING,

    /** Connected with node info available. */
    CONNECTED_WITH_NODE,
}
