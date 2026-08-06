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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.location.LocationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.domain.usecase.settings.MeshLocationUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.SetProvideLocationUseCase
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.UiPrefs
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.location_disabled
import com.ntsocial.meshlink.core.resources.position_config_set_fixed_from_phone
import com.ntsocial.meshlink.core.resources.provide_location_fixed_summary
import com.ntsocial.meshlink.core.resources.provide_location_to_mesh
import com.ntsocial.meshlink.core.resources.provide_location_to_mesh_summary
import com.ntsocial.meshlink.core.ui.component.SwitchPreference
import com.ntsocial.meshlink.core.ui.component.TitledCard
import com.ntsocial.meshlink.core.ui.util.isGpsDisabled
import com.ntsocial.meshlink.core.ui.util.isLocationPermissionGranted
import com.ntsocial.meshlink.core.ui.util.rememberRequestLocationPermission
import com.ntsocial.meshlink.core.ui.util.rememberShowToastResource
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
actual fun PhoneLocationSharingPreference(enabled: Boolean, fixedPosition: Boolean) {
    val nodeRepository = koinInject<NodeRepository>()
    val uiPrefs = koinInject<UiPrefs>()
    val setProvideLocation = koinInject<SetProvideLocationUseCase>()
    val meshLocation = koinInject<MeshLocationUseCase>()
    val nodeInfo by nodeRepository.myNodeInfo.collectAsStateWithLifecycle()
    val nodeNum = nodeInfo?.myNodeNum
    val provideLocationFlow = remember(nodeNum) { nodeNum?.let(uiPrefs::shouldProvideNodeLocation) ?: flowOf(false) }
    val provideLocation by provideLocationFlow.collectAsStateWithLifecycle(initialValue = false)
    val permissionGranted = isLocationPermissionGranted()
    val gpsDisabled = isGpsDisabled()
    val showToast = rememberShowToastResource()
    val coroutineScope = rememberCoroutineScope()

    val requestPermission =
        rememberRequestLocationPermission(
            onGranted = { enablePhoneLocationSharing(nodeNum, setProvideLocation, meshLocation) },
        )

    LaunchedEffect(provideLocation, enabled, fixedPosition, permissionGranted, gpsDisabled, nodeNum) {
        if (provideLocation && enabled && !fixedPosition && nodeNum != null) {
            // The preference may already be true when this screen opens, so no switch callback would otherwise ask
            // MeshService to promote a background-started connectedDevice service to the location type.
            meshLocation.startProvidingLocation()
            when {
                gpsDisabled -> showToast(Res.string.location_disabled)
                !permissionGranted -> requestPermission()
            }
        }
    }

    TitledCard(title = stringResource(Res.string.provide_location_to_mesh)) {
        SwitchPreference(
            title = stringResource(Res.string.provide_location_to_mesh),
            summary =
            stringResource(
                if (fixedPosition) {
                    Res.string.provide_location_fixed_summary
                } else {
                    Res.string.provide_location_to_mesh_summary
                },
            ),
            checked = provideLocation,
            enabled = enabled && nodeNum != null && (!fixedPosition || provideLocation),
            onCheckedChange = { shouldProvide ->
                handlePhoneLocationSharingToggle(
                    shouldProvide = shouldProvide,
                    nodeNum = nodeNum,
                    gpsDisabled = gpsDisabled,
                    permissionGranted = permissionGranted,
                    setProvideLocation = setProvideLocation,
                    meshLocation = meshLocation,
                    requestPermission = requestPermission,
                    showLocationDisabled = { coroutineScope.launch { showToast(Res.string.location_disabled) } },
                )
            },
        )
    }
}

private fun enablePhoneLocationSharing(
    nodeNum: Int?,
    setProvideLocation: SetProvideLocationUseCase,
    meshLocation: MeshLocationUseCase,
) {
    if (nodeNum == null) return
    setProvideLocation(nodeNum, true)
    meshLocation.startProvidingLocation()
}

private fun handlePhoneLocationSharingToggle(
    shouldProvide: Boolean,
    nodeNum: Int?,
    gpsDisabled: Boolean,
    permissionGranted: Boolean,
    setProvideLocation: SetProvideLocationUseCase,
    meshLocation: MeshLocationUseCase,
    requestPermission: () -> Unit,
    showLocationDisabled: () -> Unit,
) {
    if (nodeNum == null) return
    when {
        !shouldProvide -> {
            setProvideLocation(nodeNum, false)
            meshLocation.stopProvidingLocation()
        }

        gpsDisabled -> showLocationDisabled()

        permissionGranted -> enablePhoneLocationSharing(nodeNum, setProvideLocation, meshLocation)

        else -> requestPermission()
    }
}
