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
package com.ntsocial.meshlink.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_calendar_month
import com.ntsocial.meshlink.core.resources.ic_layers
import com.ntsocial.meshlink.core.resources.ic_lens
import com.ntsocial.meshlink.core.resources.ic_location_disabled
import com.ntsocial.meshlink.core.resources.ic_location_on
import com.ntsocial.meshlink.core.resources.ic_map
import com.ntsocial.meshlink.core.resources.ic_my_location
import com.ntsocial.meshlink.core.resources.ic_navigation
import com.ntsocial.meshlink.core.resources.ic_pin_drop
import com.ntsocial.meshlink.core.resources.ic_place
import com.ntsocial.meshlink.core.resources.ic_route
import com.ntsocial.meshlink.core.resources.ic_trip_origin
import com.ntsocial.meshlink.core.resources.ic_tune
import org.jetbrains.compose.resources.vectorResource

// Map control icons
val MeshtasticIcons.Layers: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_layers)
val MeshtasticIcons.MyLocation: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_my_location)
val MeshtasticIcons.LocationDisabled: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_location_disabled)
val MeshtasticIcons.PinDrop: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_pin_drop)
val MeshtasticIcons.TripOrigin: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_trip_origin)
val MeshtasticIcons.CalendarMonth: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_calendar_month)
val MeshtasticIcons.MapCompass: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_navigation)
val MeshtasticIcons.Tune: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_tune)
val MeshtasticIcons.Place: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_place)
val MeshtasticIcons.Lens: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_lens)
val MeshtasticIcons.Map: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_map)
val MeshtasticIcons.LocationOn: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_location_on)
val MeshtasticIcons.Route: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_route)
