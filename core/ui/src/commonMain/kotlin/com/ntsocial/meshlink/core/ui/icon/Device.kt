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
package com.ntsocial.meshlink.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_android
import com.ntsocial.meshlink.core.resources.ic_fingerprint
import com.ntsocial.meshlink.core.resources.ic_fork_left
import com.ntsocial.meshlink.core.resources.ic_home
import com.ntsocial.meshlink.core.resources.ic_icecream
import com.ntsocial.meshlink.core.resources.ic_memory
import com.ntsocial.meshlink.core.resources.ic_military_tech
import com.ntsocial.meshlink.core.resources.ic_mountain_flag
import com.ntsocial.meshlink.core.resources.ic_my_location
import com.ntsocial.meshlink.core.resources.ic_numbers
import com.ntsocial.meshlink.core.resources.ic_person
import com.ntsocial.meshlink.core.resources.ic_person_off
import com.ntsocial.meshlink.core.resources.ic_phone_android
import com.ntsocial.meshlink.core.resources.ic_router
import com.ntsocial.meshlink.core.resources.ic_search
import com.ntsocial.meshlink.core.resources.ic_sensors
import com.ntsocial.meshlink.core.resources.ic_visibility_off
import com.ntsocial.meshlink.core.resources.ic_work
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.proto.Config

val MeshtasticIcons.Role: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_work)
val MeshtasticIcons.NodeId: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_fingerprint)

/** Returns a specific icon for a given [Config.DeviceConfig.Role]. */
@Composable
fun MeshtasticIcons.role(role: Config.DeviceConfig.Role?): ImageVector = when (role) {
    Config.DeviceConfig.Role.CLIENT -> vectorResource(Res.drawable.ic_person)
    Config.DeviceConfig.Role.CLIENT_MUTE -> vectorResource(Res.drawable.ic_person_off)
    Config.DeviceConfig.Role.ROUTER -> vectorResource(Res.drawable.ic_mountain_flag)
    Config.DeviceConfig.Role.TRACKER -> vectorResource(Res.drawable.ic_my_location)
    Config.DeviceConfig.Role.SENSOR -> vectorResource(Res.drawable.ic_sensors)
    Config.DeviceConfig.Role.TAK -> vectorResource(Res.drawable.ic_military_tech)
    Config.DeviceConfig.Role.TAK_TRACKER -> vectorResource(Res.drawable.ic_my_location)
    Config.DeviceConfig.Role.CLIENT_HIDDEN -> vectorResource(Res.drawable.ic_visibility_off)
    Config.DeviceConfig.Role.LOST_AND_FOUND -> vectorResource(Res.drawable.ic_search)
    Config.DeviceConfig.Role.CLIENT_BASE -> vectorResource(Res.drawable.ic_home)
    Config.DeviceConfig.Role.ROUTER_LATE -> vectorResource(Res.drawable.ic_router)
    else -> vectorResource(Res.drawable.ic_work)
}

val MeshtasticIcons.Device: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_router)

val MeshtasticIcons.PhoneAndroid: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_phone_android)
val MeshtasticIcons.ForkLeft: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_fork_left)
val MeshtasticIcons.Icecream: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_icecream)
val MeshtasticIcons.DeviceNumbers: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_numbers)
val MeshtasticIcons.Android: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_android)
val MeshtasticIcons.HardwareModel: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_memory)
