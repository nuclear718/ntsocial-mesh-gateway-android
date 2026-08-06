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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.Channel
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.accept
import com.ntsocial.meshlink.core.resources.add
import com.ntsocial.meshlink.core.resources.add_channels_description
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.channel_apply_failed
import com.ntsocial.meshlink.core.resources.channel_apply_in_progress
import com.ntsocial.meshlink.core.resources.new_channel_rcvd
import com.ntsocial.meshlink.core.resources.replace
import com.ntsocial.meshlink.core.resources.replace_channels_and_settings_description
import com.ntsocial.meshlink.core.ui.component.ChannelSelection
import com.ntsocial.meshlink.core.ui.util.getChannelPreviewForAdd
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.proto.ChannelSet

@Composable
fun ScannedQrCodeDialog(
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    viewModel: ScannedQrCodeViewModel = koinViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val maxChannels by viewModel.maxChannels.collectAsStateWithLifecycle()
    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(applyState) {
        if (applyState == ChannelQrApplyState.Verified) {
            viewModel.clearApplyState()
            currentOnDismiss()
        }
    }

    ScannedQrCodeDialog(
        channels = channels,
        incoming = incoming,
        onDismiss = onDismiss,
        maxChannels = maxChannels,
        applyState = applyState,
        onConfirm = viewModel::setChannels,
    )
}

/** Enables the user to select which channels to accept after scanning a QR code. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ScannedQrCodeDialog(
    channels: ChannelSet,
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    maxChannels: Int = DEFAULT_MAX_CHANNELS,
    applyState: ChannelQrApplyState = ChannelQrApplyState.Idle,
    onConfirm: (ChannelSet) -> Unit,
) {
    var shouldReplace by rememberSaveable { mutableStateOf(incoming.lora_config != null) }
    val effectiveMaxChannels = rememberSaveable { maxChannels }

    val addPreview =
        remember(channels.settings, incoming.settings, channels.lora_config, effectiveMaxChannels) {
            getChannelPreviewForAdd(
                existing = channels.settings,
                incoming = incoming.settings,
                loraConfig = channels.lora_config ?: Channel.default.loraConfig,
                maxChannels = effectiveMaxChannels,
            )
        }

    val channelSet =
        remember(shouldReplace, channels, incoming, addPreview.settings) {
            if (shouldReplace) {
                // When replacing, apply the incoming LoRa configuration but preserve certain
                // locally safe fields such as MQTT flags and TX power. This prevents QR codes
                // from unintentionally overriding device-specific power limits (e.g. E22 caps).
                incoming.copy(
                    lora_config =
                    incoming.lora_config?.copy(
                        config_ok_to_mqtt = channels.lora_config?.config_ok_to_mqtt ?: false,
                        tx_power = channels.lora_config?.tx_power ?: 0,
                    ),
                )
            } else {
                channels.copy(settings = addPreview.settings)
            }
        }

    val modemPresetName = Channel(loraConfig = channelSet.lora_config ?: Channel.default.loraConfig).name

    /* Holds selections made by the user */
    val channelSelections =
        remember(shouldReplace, channelSet.settings, addPreview.selections) {
            val defaults =
                if (shouldReplace) {
                    List(channelSet.settings.size) { true }
                } else {
                    addPreview.selections
                }
            mutableStateListOf<Boolean>().apply { addAll(defaults) }
        }

    val selectedChannelSet =
        if (shouldReplace) {
            channelSet.copy(
                settings = channelSet.settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true },
            )
        } else {
            channelSet.copy(
                settings =
                channelSet.settings.filterIndexed { i, _ ->
                    val isExisting = i < channels.settings.size
                    isExisting || channelSelections.getOrNull(i) == true
                },
            )
        }

    // Compute LoRa configuration changes when in replace mode
    val loraChanges =
        remember(shouldReplace, channels, incoming) {
            if (shouldReplace && incoming.lora_config != null) {
                val current = channels.lora_config
                val new = incoming.lora_config
                val changes = mutableListOf<String>()

                if (current?.hop_limit != new?.hop_limit) {
                    changes.add("Hop Limit: ${current?.hop_limit} -> ${new?.hop_limit}")
                }
                if (current?.region != new?.region) {
                    val currentRegionDesc = current?.region?.name ?: "Unknown"
                    val newRegionDesc = new?.region?.name ?: "Unknown"
                    changes.add("Region: $currentRegionDesc -> $newRegionDesc")
                }
                if (current?.modem_preset != new?.modem_preset) {
                    val currentPresetDesc = current?.modem_preset?.name ?: "Unknown"
                    val newPresetDesc = new?.modem_preset?.name ?: "Unknown"
                    changes.add("Modem Preset: $currentPresetDesc -> $newPresetDesc")
                }
                if (current?.use_preset != new?.use_preset) {
                    changes.add("Use Preset: ${current?.use_preset} -> ${new?.use_preset}")
                }

                changes
            } else {
                emptyList()
            }
        }

    val isApplying = applyState == ChannelQrApplyState.Applying
    Dialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !isApplying),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.new_channel_rcvd),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                item {
                    Text(
                        text =
                        stringResource(
                            if (shouldReplace) {
                                Res.string.replace_channels_and_settings_description
                            } else {
                                Res.string.add_channels_description
                            },
                        ),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                itemsIndexed(channelSet.settings) { index, channel ->
                    val isExisting = !shouldReplace && index < channels.settings.size
                    val channelObj = Channel(channel, channelSet.lora_config ?: Channel.default.loraConfig)
                    ChannelSelection(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled = !isExisting,
                        isSelected = if (isExisting) true else channelSelections[index],
                        onSelected = {
                            if (it || selectedChannelSet.settings.size > 1) {
                                channelSelections[index] = it
                            }
                        },
                        channel = channelObj,
                    )
                }

                // Display LoRa configuration changes when in replace mode
                if (shouldReplace && loraChanges.isNotEmpty()) {
                    item {
                        Text(
                            text = "LoRa Configuration Changes:",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        loraChanges.forEach { change ->
                            Text(
                                text = "• $change",
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.padding(vertical = 20.dp)) {
                        val selectedColors = ButtonDefaults.buttonColors()
                        val unselectedColors =
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        val mediumHeight = ButtonDefaults.MediumContainerHeight
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        OutlinedButton(
                            onClick = { shouldReplace = false },
                            shapes = ButtonDefaults.shapesFor(mediumHeight),
                            modifier = Modifier.height(mediumHeight).weight(1f),
                            colors = if (!shouldReplace) selectedColors else unselectedColors,
                        ) {
                            Text(
                                text = stringResource(Res.string.add),
                                style = ButtonDefaults.textStyleFor(mediumHeight),
                            )
                        }

                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        OutlinedButton(
                            onClick = { shouldReplace = true },
                            shapes = ButtonDefaults.shapesFor(mediumHeight),
                            modifier = Modifier.height(mediumHeight).weight(1f),
                            enabled = incoming.lora_config != null,
                            colors = if (shouldReplace) selectedColors else unselectedColors,
                        ) {
                            Text(
                                text = stringResource(Res.string.replace),
                                style = ButtonDefaults.textStyleFor(mediumHeight),
                            )
                        }
                    }
                }

                /* User Actions via buttons */
                item {
                    when (applyState) {
                        ChannelQrApplyState.Applying ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(Res.string.channel_apply_in_progress))
                            }

                        is ChannelQrApplyState.Failed ->
                            Text(
                                text = stringResource(Res.string.channel_apply_failed),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )

                        else -> Unit
                    }
                }

                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        TextButton(onClick = { onDismiss() }, enabled = !isApplying) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                color = MaterialTheme.colorScheme.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        TextButton(
                            onClick = { onConfirm(selectedChannelSet) },
                            enabled = !isApplying && selectedChannelSet.settings.size in 1..effectiveMaxChannels,
                        ) {
                            Text(
                                text = stringResource(Res.string.accept),
                                color = MaterialTheme.colorScheme.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ScannedQrCodeDialogPreview() {
    ScannedQrCodeDialog(
        channels = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
        incoming = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
        onDismiss = {},
        onConfirm = {},
    )
}
