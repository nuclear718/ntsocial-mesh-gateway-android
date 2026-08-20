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
import com.ntsocial.meshlink.core.model.normalizeReliableChannelSettings
import com.ntsocial.meshlink.core.repository.CHANNEL_APPLY_RESTART_SECONDS
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.accept
import com.ntsocial.meshlink.core.resources.add
import com.ntsocial.meshlink.core.resources.add_channels_description
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.channel_apply_accept_notice
import com.ntsocial.meshlink.core.resources.new_channel_rcvd
import com.ntsocial.meshlink.core.resources.replace
import com.ntsocial.meshlink.core.resources.replace_channels_and_settings_description
import com.ntsocial.meshlink.core.resources.replace_secondary_channels_description
import com.ntsocial.meshlink.core.ui.component.ChannelApplyStatus
import com.ntsocial.meshlink.core.ui.component.ChannelApplyUiState
import com.ntsocial.meshlink.core.ui.component.ChannelSelection
import com.ntsocial.meshlink.core.ui.util.getChannelPreviewForAdd
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

private enum class ChannelQrImportMode {
    ADD,
    SECONDARY_REPLACE,
    FULL_REPLACE,
}

/** Keeps selected existing slots stable by filling explicitly released secondary slots before appending QR channels. */
private fun buildSecondaryReplacementSettings(
    settings: List<ChannelSettings>,
    selections: List<Boolean>,
    existingCount: Int,
): List<ChannelSettings> {
    if (existingCount <= 0) return emptyList()
    val selectedIncoming =
        settings
            .drop(existingCount)
            .filterIndexed { index, _ -> selections.getOrNull(existingCount + index) == true }
            .iterator()

    return buildList {
        settings.take(existingCount).forEachIndexed { index, current ->
            when {
                index == 0 -> add(current)
                current != ChannelSettings() && selections.getOrNull(index) == true -> add(current)
                selectedIncoming.hasNext() -> add(selectedIncoming.next())
            }
        }
        while (selectedIncoming.hasNext()) add(selectedIncoming.next())
    }
}

/** Rejects a selection that would compact a retained secondary into an earlier, unfilled slot. */
private fun keepsRetainedSecondarySlotsStable(
    currentSettings: List<ChannelSettings>,
    selections: List<Boolean>,
    desiredSettings: List<ChannelSettings>,
    currentLora: Config.LoRaConfig?,
): Boolean {
    val normalizedDesired = normalizeReliableChannelSettings(desiredSettings, currentLora)
    return (1..currentSettings.lastIndex).all { index ->
        val current = currentSettings[index]
        current == ChannelSettings() ||
            selections.getOrNull(index) != true ||
            normalizedDesired.getOrNull(index) == current
    }
}

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
    var dialogPrepared by remember(incoming) { mutableStateOf(false) }

    LaunchedEffect(incoming) {
        viewModel.onDialogShown()
        dialogPrepared = true
    }

    LaunchedEffect(applyState, dialogPrepared) {
        if (!dialogPrepared) return@LaunchedEffect
        when (applyState) {
            ChannelApplyUiState.Verified,
            is ChannelApplyUiState.Failed,
            -> {
                viewModel.clearApplyState()
                currentOnDismiss()
            }

            else -> Unit
        }
    }

    ScannedQrCodeDialog(
        channels = channels,
        incoming = incoming,
        onDismiss = {
            viewModel.onDialogDismissed()
            currentOnDismiss()
        },
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
    applyState: ChannelApplyUiState = ChannelApplyUiState.Idle,
    onConfirm: (ChannelSet) -> Unit,
) {
    var shouldReplace by rememberSaveable(incoming) { mutableStateOf(incoming.lora_config != null) }
    val effectiveMaxChannels = maxChannels
    val hasCurrentPrimary = channels.settings.firstOrNull()?.let { it != ChannelSettings() } == true
    val hasCurrentRadioState = hasCurrentPrimary && channels.lora_config != null
    val importMode =
        when {
            !shouldReplace -> ChannelQrImportMode.ADD
            incoming.lora_config == null -> ChannelQrImportMode.SECONDARY_REPLACE
            else -> ChannelQrImportMode.FULL_REPLACE
        }

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
        remember(importMode, channels, incoming, addPreview.settings) {
            if (importMode == ChannelQrImportMode.FULL_REPLACE) {
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
        remember(importMode, channelSet.settings, addPreview.selections) {
            val defaults =
                if (importMode == ChannelQrImportMode.FULL_REPLACE) {
                    List(channelSet.settings.size) { true }
                } else {
                    addPreview.selections.mapIndexed { index, selected ->
                        selected &&
                            !(
                                importMode == ChannelQrImportMode.SECONDARY_REPLACE &&
                                    index < channels.settings.size &&
                                    channelSet.settings.getOrNull(index) == ChannelSettings()
                                )
                    }
                }
            mutableStateListOf<Boolean>().apply { addAll(defaults) }
        }

    val selectedChannelSet =
        when (importMode) {
            ChannelQrImportMode.FULL_REPLACE ->
                channelSet.copy(
                    settings = channelSet.settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true },
                )

            ChannelQrImportMode.SECONDARY_REPLACE ->
                channelSet.copy(
                    settings =
                    buildSecondaryReplacementSettings(
                        settings = channelSet.settings,
                        selections = channelSelections,
                        existingCount = channels.settings.size,
                    ),
                    // An add-only QR deliberately carries no LoRa configuration. Resolve the current value under the
                    // exact radio/mutation lock instead of copying a potentially stale UI snapshot back to the MCU.
                    lora_config = null,
                )

            ChannelQrImportMode.ADD ->
                channelSet.copy(
                    settings =
                    channelSet.settings.filterIndexed { i, _ ->
                        val isExisting = i < channels.settings.size
                        isExisting || channelSelections.getOrNull(i) == true
                    },
                    // ADD is channel-only even when the QR also contains a radio configuration. Resolve the current
                    // LoRa value inside the serialized transaction instead of returning this UI snapshot to the MCU.
                    lora_config = null,
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

    val isApplying = applyState == ChannelApplyUiState.Applying
    val keepsRetainedSlotsStable =
        importMode != ChannelQrImportMode.SECONDARY_REPLACE ||
            keepsRetainedSecondarySlotsStable(
                currentSettings = channels.settings,
                selections = channelSelections,
                desiredSettings = selectedChannelSet.settings,
                currentLora = channels.lora_config,
            )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                            when (importMode) {
                                ChannelQrImportMode.ADD -> Res.string.add_channels_description

                                ChannelQrImportMode.SECONDARY_REPLACE ->
                                    Res.string.replace_secondary_channels_description

                                ChannelQrImportMode.FULL_REPLACE ->
                                    Res.string.replace_channels_and_settings_description
                            },
                        ),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                itemsIndexed(channelSet.settings) { index, channel ->
                    val isExisting = index < channels.settings.size
                    val isPrimary = index == 0
                    val isExistingPlaceholder = isExisting && channel == ChannelSettings()
                    val channelObj = Channel(channel, channelSet.lora_config ?: Channel.default.loraConfig)
                    val forcedSelection: Boolean? =
                        when (importMode) {
                            ChannelQrImportMode.ADD -> if (isExisting) true else null

                            ChannelQrImportMode.SECONDARY_REPLACE ->
                                when {
                                    isExistingPlaceholder -> false
                                    isPrimary -> true
                                    else -> null
                                }

                            ChannelQrImportMode.FULL_REPLACE -> null
                        }
                    ChannelSelection(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled =
                        when (importMode) {
                            ChannelQrImportMode.ADD -> !isExisting
                            ChannelQrImportMode.SECONDARY_REPLACE -> !isPrimary && !isExistingPlaceholder
                            ChannelQrImportMode.FULL_REPLACE -> true
                        },
                        isSelected = forcedSelection ?: channelSelections[index],
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
                            enabled = hasCurrentRadioState && incoming.settings.isNotEmpty(),
                            colors = if (shouldReplace) selectedColors else unselectedColors,
                        ) {
                            Text(
                                text = stringResource(Res.string.replace),
                                style = ButtonDefaults.textStyleFor(mediumHeight),
                            )
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text =
                            stringResource(Res.string.channel_apply_accept_notice, CHANNEL_APPLY_RESTART_SECONDS),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                /* User Actions via buttons */
                item {
                    if (
                        applyState == ChannelApplyUiState.Applying ||
                        applyState == ChannelApplyUiState.WaitingForReconnect ||
                        applyState == ChannelApplyUiState.InvalidSettings
                    ) {
                        ChannelApplyStatus(state = applyState, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }

                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                color = MaterialTheme.colorScheme.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        TextButton(
                            onClick = {
                                // The notice above explains the expected restart before this synchronously claimed
                                // transaction leaves the dialog and continues acknowledgement/readback in background.
                                onConfirm(selectedChannelSet)
                                onDismiss()
                            },
                            enabled =
                            hasCurrentRadioState &&
                                !isApplying &&
                                keepsRetainedSlotsStable &&
                                selectedChannelSet.settings.size in 1..effectiveMaxChannels,
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
