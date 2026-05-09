/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.ui.util.MapViewProvider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single

/** Google Maps implementation of [MapViewProvider]. */
@Single
class GoogleMapViewProvider : MapViewProvider {
    @Composable
    override fun MapView(modifier: Modifier, navigateToNodeDetails: (Int) -> Unit, waypointId: Int?) {
        val mapViewModel: MapViewModel = koinViewModel()
        LaunchedEffect(waypointId) { mapViewModel.setWaypointId(waypointId) }
        com.ntsocial.meshlink.app.map.MapView(
            modifier = modifier,
            mapViewModel = mapViewModel,
            navigateToNodeDetails = navigateToNodeDetails,
        )
    }
}
