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
package com.ntsocial.meshlink.feature.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.app_settings
import com.ntsocial.meshlink.core.resources.location_disabled
import com.ntsocial.meshlink.core.resources.provide_location_to_mesh
import com.ntsocial.meshlink.core.ui.component.SwitchListItem
import com.ntsocial.meshlink.core.ui.icon.LocationOn
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.util.isGpsDisabled
import com.ntsocial.meshlink.core.ui.util.isLocationPermissionGranted
import com.ntsocial.meshlink.core.ui.util.rememberRequestLocationPermission
import com.ntsocial.meshlink.core.ui.util.rememberShowToastResource
import org.jetbrains.compose.resources.stringResource

/** Section managing local privacy settings such as optional phone-location forwarding. */
@Composable
fun PrivacySection(
    provideLocation: Boolean,
    onToggleLocation: (Boolean) -> Unit,
    homoglyphEnabled: Boolean,
    onToggleHomoglyph: () -> Unit,
    startProvideLocation: () -> Unit,
    stopProvideLocation: () -> Unit,
) {
    val showToast = rememberShowToastResource()
    val isLocationGranted = isLocationPermissionGranted()
    val isGpsOff = isGpsDisabled()
    val requestLocationPermission =
        rememberRequestLocationPermission(
            onGranted = { startProvideLocation() },
            onDenied = {
                onToggleLocation(false)
                stopProvideLocation()
            },
        )

    LaunchedEffect(provideLocation, isLocationGranted, isGpsOff) {
        if (provideLocation) {
            if (isLocationGranted) {
                if (!isGpsOff) {
                    startProvideLocation()
                } else {
                    showToast(Res.string.location_disabled)
                }
            } else {
                requestLocationPermission()
            }
        } else {
            stopProvideLocation()
        }
    }

    ExpressiveSection(title = stringResource(Res.string.app_settings)) {
        SwitchListItem(
            text = stringResource(Res.string.provide_location_to_mesh),
            leadingIcon = MeshtasticIcons.LocationOn,
            enabled = !isGpsOff || provideLocation,
            checked = provideLocation,
            onClick = { onToggleLocation(!provideLocation) },
        )

        HomoglyphSetting(homoglyphEncodingEnabled = homoglyphEnabled, onToggle = onToggleHomoglyph)
    }
}
