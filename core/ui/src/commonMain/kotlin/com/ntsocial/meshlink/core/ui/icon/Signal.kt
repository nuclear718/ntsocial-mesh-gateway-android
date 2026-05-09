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
import com.ntsocial.meshlink.core.resources.ic_cell_tower
import com.ntsocial.meshlink.core.resources.ic_cruelty_free
import com.ntsocial.meshlink.core.resources.ic_graphic_eq
import com.ntsocial.meshlink.core.resources.ic_hub
import com.ntsocial.meshlink.core.resources.ic_near_me
import com.ntsocial.meshlink.core.resources.ic_podcasts
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_0_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_1_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_2_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_3_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_4_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_alt
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_alt_1_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_alt_2_bar
import com.ntsocial.meshlink.core.resources.ic_signal_cellular_off
import com.ntsocial.meshlink.core.resources.ic_ssid_chart
import com.ntsocial.meshlink.core.resources.ic_tsunami
import com.ntsocial.meshlink.core.resources.ic_wifi_channel
import org.jetbrains.compose.resources.vectorResource

val MeshtasticIcons.HopCount: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cruelty_free)
val MeshtasticIcons.Channel: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_wifi_channel)
val MeshtasticIcons.AirUtilization: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_ssid_chart)

// Signal measurement metrics
val MeshtasticIcons.Snr: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_graphic_eq)
val MeshtasticIcons.Rssi: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_podcasts)

val MeshtasticIcons.SignalCellular0Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_0_bar)

val MeshtasticIcons.SignalCellular1Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_1_bar)

val MeshtasticIcons.SignalCellular2Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_2_bar)

val MeshtasticIcons.SignalCellular3Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_3_bar)

val MeshtasticIcons.SignalCellular4Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_4_bar)

val MeshtasticIcons.MeshHub: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_hub)
val MeshtasticIcons.NearMe: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_near_me)
val MeshtasticIcons.Tsunami: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_tsunami)

val MeshtasticIcons.SignalOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_off)
val MeshtasticIcons.SignalAlt1Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_alt_1_bar)
val MeshtasticIcons.SignalAlt2Bar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_alt_2_bar)
val MeshtasticIcons.CellTower: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cell_tower)
val MeshtasticIcons.ChannelUtilization: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_signal_cellular_alt)
