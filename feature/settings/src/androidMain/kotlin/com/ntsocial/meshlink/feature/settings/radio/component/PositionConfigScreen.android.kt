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
package com.ntsocial.meshlink.feature.settings.radio.component

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.domain.usecase.settings.MeshLocationUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.SetPreciseLocationSharingUseCase
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.model.PreciseLocationChannelOption
import com.ntsocial.meshlink.core.model.PreciseLocationChannelSetPlanner
import com.ntsocial.meshlink.core.repository.CHANNEL_APPLY_RESTART_SECONDS
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.UiPrefs
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.apply
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.channel_apply_failed
import com.ntsocial.meshlink.core.resources.channel_apply_in_progress
import com.ntsocial.meshlink.core.resources.channel_apply_invalid
import com.ntsocial.meshlink.core.resources.channel_apply_pending
import com.ntsocial.meshlink.core.resources.channel_apply_rejected
import com.ntsocial.meshlink.core.resources.location_disabled
import com.ntsocial.meshlink.core.resources.position_config_set_fixed_from_phone
import com.ntsocial.meshlink.core.resources.precise_location_active_summary
import com.ntsocial.meshlink.core.resources.precise_location_change_channel
import com.ntsocial.meshlink.core.resources.precise_location_channel_label
import com.ntsocial.meshlink.core.resources.precise_location_choose_channel
import com.ntsocial.meshlink.core.resources.precise_location_firmware_precision_limited_warning
import com.ntsocial.meshlink.core.resources.precise_location_mqtt_warning
import com.ntsocial.meshlink.core.resources.precise_location_needs_setup
import com.ntsocial.meshlink.core.resources.precise_location_no_channels
import com.ntsocial.meshlink.core.resources.precise_location_off_summary
import com.ntsocial.meshlink.core.resources.precise_location_public_acknowledgement
import com.ntsocial.meshlink.core.resources.precise_location_public_warning
import com.ntsocial.meshlink.core.resources.precise_location_setup_notice
import com.ntsocial.meshlink.core.resources.precise_location_sharing
import com.ntsocial.meshlink.core.resources.provide_location_fixed_summary
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.core.ui.util.isGpsDisabled
import com.ntsocial.meshlink.core.ui.util.isLocationPermissionGranted
import com.ntsocial.meshlink.core.ui.util.rememberRequestLocationPermission
import com.ntsocial.meshlink.core.ui.util.rememberShowToastResource
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig

@Composable
actual fun DeviceLocationButton(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    onLocationReceived: (Position) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    TextButton(
        enabled = enabled,
        onClick = {
            @SuppressLint("MissingPermission")
            coroutineScope.launch {
                val phoneLoc = viewModel.getCurrentLocation()
                if (phoneLoc != null) {
                    val locationInput =
                        Position(
                            latitude = phoneLoc.latitude,
                            longitude = phoneLoc.longitude,
                            altitude =
                            LocationCompat.hasMslAltitude(phoneLoc).let {
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    phoneLoc.mslAltitudeMeters.toInt()
                                } else {
                                    phoneLoc.altitude.toInt()
                                }
                            },
                        )
                    onLocationReceived(locationInput)
                }
            }
        },
    ) {
        Text(text = stringResource(Res.string.position_config_set_fixed_from_phone))
    }
}

@Composable
@Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod", "ModifierMissing")
actual fun PhoneLocationSharingPreference(enabled: Boolean, fixedPosition: Boolean) {
    val nodeRepository = koinInject<NodeRepository>()
    val uiPrefs = koinInject<UiPrefs>()
    val radioConfigRepository = koinInject<RadioConfigRepository>()
    val setPreciseLocation = koinInject<SetPreciseLocationSharingUseCase>()
    val meshLocation = koinInject<MeshLocationUseCase>()
    val channelMutationLock = koinInject<ChannelMutationLock>()
    val nodeInfo by nodeRepository.myNodeInfo.collectAsStateWithLifecycle()
    val nodeNum = nodeInfo?.myNodeNum
    val admissionFlow = remember(nodeNum) { nodeNum?.let(uiPrefs::preciseLocationAdmission) }
    val admission by
        admissionFlow?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(com.ntsocial.meshlink.core.repository.PreciseLocationAdmission()) }
    val channelSet by radioConfigRepository.channelSetFlow.collectAsStateWithLifecycle(initialValue = ChannelSet())
    val committedConfig by
        radioConfigRepository.localConfigFlow.collectAsStateWithLifecycle(initialValue = LocalConfig())
    val channelMutationOwnerCount by channelMutationLock.activeOrPendingOwners.collectAsStateWithLifecycle()
    val channelMutationInProgress = channelMutationOwnerCount > 0
    val options = remember(channelSet) { PreciseLocationChannelSetPlanner.activeSecondaryOptions(channelSet) }
    val selectedOption = options.firstOrNull { it.index == admission.channelIndex }
    val channelPolicyVerified =
        admission.enabled &&
            admission.channelIdentity.isNotBlank() &&
            PreciseLocationChannelSetPlanner.matchesPolicy(
                channelSet,
                admission.channelIndex,
                admission.channelIdentity,
            )
    val committedFixedPosition = committedConfig.position?.fixed_position == true
    val effectiveFixedPosition = fixedPosition || committedFixedPosition
    val permissionGranted = isLocationPermissionGranted()
    val gpsDisabled = isGpsDisabled()
    val showToast = rememberShowToastResource()
    val coroutineScope = rememberCoroutineScope()
    var showChannelDialog by rememberSaveable { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ChannelReliabilityResult?>(null) }

    val requestPermission = rememberRequestLocationPermission(onGranted = { showChannelDialog = true })

    LaunchedEffect(channelPolicyVerified, enabled, effectiveFixedPosition, permissionGranted, gpsDisabled, nodeNum) {
        if (
            channelPolicyVerified &&
            enabled &&
            !effectiveFixedPosition &&
            permissionGranted &&
            !gpsDisabled &&
            nodeNum != null
        ) {
            meshLocation.startProvidingLocation()
        } else if (!channelPolicyVerified || effectiveFixedPosition) {
            meshLocation.stopProvidingLocation()
        }
    }

    if (showChannelDialog) {
        PreciseLocationChannelDialog(
            options = options,
            selectedIndex = admission.channelIndex,
            confirmationEnabled = !operationInProgress && !channelMutationInProgress,
            onDismiss = { showChannelDialog = false },
            onConfirm = { option ->
                val currentNodeNum = nodeNum ?: return@PreciseLocationChannelDialog
                showChannelDialog = false
                operationInProgress = true
                lastResult = null
                meshLocation.stopProvidingLocation()
                coroutineScope.launch {
                    val result =
                        withContext(NonCancellable) {
                            setPreciseLocation.enable(currentNodeNum, option.index, option.channelIdentity)
                        }
                    lastResult = result
                    operationInProgress = false
                    if (
                        result == ChannelReliabilityResult.VERIFIED &&
                        permissionGranted &&
                        !gpsDisabled &&
                        !effectiveFixedPosition
                    ) {
                        meshLocation.startProvidingLocation()
                    } else {
                        meshLocation.stopProvidingLocation()
                    }
                }
            },
        )
    }

    val summary =
        when {
            effectiveFixedPosition -> stringResource(Res.string.provide_location_fixed_summary)

            operationInProgress -> stringResource(Res.string.channel_apply_in_progress, CHANNEL_APPLY_RESTART_SECONDS)

            lastResult == ChannelReliabilityResult.VERIFICATION_PENDING ->
                stringResource(Res.string.channel_apply_pending, CHANNEL_APPLY_RESTART_SECONDS)

            lastResult == ChannelReliabilityResult.INVALID_CHANNEL_SET ->
                stringResource(Res.string.channel_apply_invalid)

            lastResult == ChannelReliabilityResult.RADIO_REJECTED -> stringResource(Res.string.channel_apply_rejected)

            lastResult != null && lastResult != ChannelReliabilityResult.VERIFIED ->
                stringResource(Res.string.channel_apply_failed)

            channelPolicyVerified && selectedOption != null ->
                stringResource(Res.string.precise_location_active_summary, selectedOption.index, selectedOption.name)

            admission.enabled -> stringResource(Res.string.precise_location_needs_setup)

            else -> stringResource(Res.string.precise_location_off_summary)
        }

    TitledCard(title = stringResource(Res.string.precise_location_sharing)) {
        PreciseLocationSharingSwitchPreference(
            title = stringResource(Res.string.precise_location_sharing),
            summary = summary,
            admissionEnabled = admission.enabled,
            radioReadyForEnable = enabled,
            nodeAvailable = nodeNum != null,
            operationInProgress = operationInProgress,
            channelMutationInProgress = channelMutationInProgress,
            fixedPosition = effectiveFixedPosition,
            onCheckedChange = { shouldProvide ->
                val currentNodeNum = nodeNum ?: return@PreciseLocationSharingSwitchPreference
                if (!shouldProvide) {
                    operationInProgress = true
                    lastResult = null
                    meshLocation.stopProvidingLocation()
                    coroutineScope.launch {
                        lastResult = withContext(NonCancellable) { setPreciseLocation.disable(currentNodeNum) }
                        operationInProgress = false
                    }
                } else {
                    when {
                        gpsDisabled -> coroutineScope.launch { showToast(Res.string.location_disabled) }
                        permissionGranted -> showChannelDialog = true
                        else -> requestPermission()
                    }
                }
            },
        )

        if (channelPolicyVerified && selectedOption != null) {
            ListItem(
                text = stringResource(Res.string.precise_location_change_channel),
                supportingText =
                stringResource(
                    Res.string.precise_location_channel_label,
                    selectedOption.index,
                    selectedOption.name,
                ),
                enabled = enabled && !operationInProgress && !channelMutationInProgress && !effectiveFixedPosition,
                onClick = { showChannelDialog = true },
            )
        }
    }
}

@Composable
internal fun PreciseLocationSharingSwitchPreference(
    title: String,
    summary: String,
    admissionEnabled: Boolean,
    radioReadyForEnable: Boolean,
    nodeAvailable: Boolean,
    operationInProgress: Boolean,
    channelMutationInProgress: Boolean,
    fixedPosition: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val state =
        preciseLocationSharingToggleState(
            admissionEnabled = admissionEnabled,
            radioReadyForEnable = radioReadyForEnable,
            nodeAvailable = nodeAvailable,
            operationInProgress = operationInProgress,
            channelMutationInProgress = channelMutationInProgress,
            fixedPosition = fixedPosition,
        )
    SwitchPreference(
        title = title,
        summary = summary,
        checked = state.checked,
        enabled = state.enabled,
        onCheckedChange = onCheckedChange,
    )
}

internal data class PreciseLocationSharingToggleState(val checked: Boolean, val enabled: Boolean)

internal fun preciseLocationSharingToggleState(
    admissionEnabled: Boolean,
    radioReadyForEnable: Boolean,
    nodeAvailable: Boolean,
    operationInProgress: Boolean,
    channelMutationInProgress: Boolean,
    fixedPosition: Boolean,
): PreciseLocationSharingToggleState = PreciseLocationSharingToggleState(
    checked = admissionEnabled,
    enabled =
    nodeAvailable &&
        !operationInProgress &&
        !channelMutationInProgress &&
        (admissionEnabled || (radioReadyForEnable && !fixedPosition)),
)

internal fun initialPreciseLocationChannelIndex(options: List<PreciseLocationChannelOption>, selectedIndex: Int): Int? =
    options.firstOrNull { it.index == selectedIndex && !it.firmwarePrecisionLimited }?.index
        ?: options.firstOrNull { !it.firmwarePrecisionLimited }?.index

@Composable
@Suppress("LongMethod")
private fun PreciseLocationChannelDialog(
    options: List<PreciseLocationChannelOption>,
    selectedIndex: Int,
    confirmationEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PreciseLocationChannelOption) -> Unit,
) {
    var pendingIndex by
        remember(options, selectedIndex) { mutableStateOf(initialPreciseLocationChannelIndex(options, selectedIndex)) }
    val pendingOption = options.firstOrNull { it.index == pendingIndex }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.precise_location_choose_channel)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (options.isEmpty()) {
                    Text(stringResource(Res.string.precise_location_no_channels))
                } else {
                    options.forEach { option ->
                        val optionEnabled = !option.firmwarePrecisionLimited
                        Row(
                            modifier =
                            Modifier.fillMaxWidth()
                                .clickable(enabled = optionEnabled) { pendingIndex = option.index }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            RadioButton(
                                selected = pendingIndex == option.index,
                                enabled = optionEnabled,
                                onClick = { pendingIndex = option.index },
                            )
                            Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                                Text(preciseChannelLabel(option))
                                if (option.firmwarePrecisionLimited) {
                                    Text(
                                        stringResource(Res.string.precise_location_firmware_precision_limited_warning),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else if (option.requiresKnownPublicWarning) {
                                    Text(
                                        stringResource(Res.string.precise_location_public_warning),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (option.mqttUplinkEnabled) {
                                    Text(
                                        stringResource(Res.string.precise_location_mqtt_warning),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(Res.string.precise_location_setup_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmationEnabled && pendingOption?.firmwarePrecisionLimited == false,
                onClick = { pendingOption?.takeUnless { it.firmwarePrecisionLimited }?.let(onConfirm) },
            ) {
                Text(
                    stringResource(
                        if (pendingOption?.requiresKnownPublicWarning == true) {
                            Res.string.precise_location_public_acknowledgement
                        } else {
                            Res.string.apply
                        },
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun preciseChannelLabel(option: PreciseLocationChannelOption): String =
    stringResource(Res.string.precise_location_channel_label, option.index, option.name)
