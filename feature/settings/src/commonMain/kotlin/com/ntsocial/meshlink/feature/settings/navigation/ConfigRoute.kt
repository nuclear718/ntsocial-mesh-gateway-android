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
package com.ntsocial.meshlink.feature.settings.navigation

import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.bluetooth
import com.ntsocial.meshlink.core.resources.channels
import com.ntsocial.meshlink.core.resources.device
import com.ntsocial.meshlink.core.resources.display
import com.ntsocial.meshlink.core.resources.ic_bluetooth
import com.ntsocial.meshlink.core.resources.ic_cell_tower
import com.ntsocial.meshlink.core.resources.ic_display_settings
import com.ntsocial.meshlink.core.resources.ic_list
import com.ntsocial.meshlink.core.resources.ic_location_on
import com.ntsocial.meshlink.core.resources.ic_person
import com.ntsocial.meshlink.core.resources.ic_power
import com.ntsocial.meshlink.core.resources.ic_router
import com.ntsocial.meshlink.core.resources.ic_security
import com.ntsocial.meshlink.core.resources.ic_wifi
import com.ntsocial.meshlink.core.resources.lora
import com.ntsocial.meshlink.core.resources.network
import com.ntsocial.meshlink.core.resources.position
import com.ntsocial.meshlink.core.resources.power
import com.ntsocial.meshlink.core.resources.security
import com.ntsocial.meshlink.core.resources.user
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.DeviceMetadata

enum class ConfigRoute(
    val title: StringResource,
    val route: Route,
    val icon: DrawableResource? = null,
    val type: Int = 0,
) {
    USER(Res.string.user, SettingsRoute.User, Res.drawable.ic_person, 0),
    CHANNELS(Res.string.channels, SettingsRoute.ChannelConfig, Res.drawable.ic_list, 0),
    DEVICE(
        Res.string.device,
        SettingsRoute.Device,
        Res.drawable.ic_router,
        AdminMessage.ConfigType.DEVICE_CONFIG.value,
    ),
    POSITION(
        Res.string.position,
        SettingsRoute.Position,
        Res.drawable.ic_location_on,
        AdminMessage.ConfigType.POSITION_CONFIG.value,
    ),
    POWER(Res.string.power, SettingsRoute.Power, Res.drawable.ic_power, AdminMessage.ConfigType.POWER_CONFIG.value),
    NETWORK(
        Res.string.network,
        SettingsRoute.Network,
        Res.drawable.ic_wifi,
        AdminMessage.ConfigType.NETWORK_CONFIG.value,
    ),
    DISPLAY(
        Res.string.display,
        SettingsRoute.Display,
        Res.drawable.ic_display_settings,
        AdminMessage.ConfigType.DISPLAY_CONFIG.value,
    ),
    LORA(Res.string.lora, SettingsRoute.LoRa, Res.drawable.ic_cell_tower, AdminMessage.ConfigType.LORA_CONFIG.value),
    BLUETOOTH(
        Res.string.bluetooth,
        SettingsRoute.Bluetooth,
        Res.drawable.ic_bluetooth,
        AdminMessage.ConfigType.BLUETOOTH_CONFIG.value,
    ),
    SECURITY(
        Res.string.security,
        SettingsRoute.Security,
        Res.drawable.ic_security,
        AdminMessage.ConfigType.SECURITY_CONFIG.value,
    ),
    ;

    companion object {
        private fun filterExcludedFrom(metadata: DeviceMetadata?): List<ConfigRoute> = entries.filter {
            when {
                metadata == null -> true

                // Include all routes if metadata is null
                it == BLUETOOTH -> metadata.hasBluetooth == true

                it == NETWORK -> metadata.hasWifi == true || metadata.hasEthernet == true

                else -> true // Include all other routes by default
            }
        }

        val radioConfigRoutes = listOf(USER, LORA, CHANNELS, SECURITY)

        fun deviceConfigRoutes(metadata: DeviceMetadata?): List<ConfigRoute> =
            filterExcludedFrom(metadata) - radioConfigRoutes
    }
}
