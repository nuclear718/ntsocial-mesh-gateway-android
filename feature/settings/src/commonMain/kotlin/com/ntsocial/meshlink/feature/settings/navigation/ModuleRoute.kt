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

import com.ntsocial.meshlink.core.model.Capabilities
import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.navigation.SettingsRoute
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ambient_lighting
import com.ntsocial.meshlink.core.resources.audio
import com.ntsocial.meshlink.core.resources.canned_message
import com.ntsocial.meshlink.core.resources.detection_sensor
import com.ntsocial.meshlink.core.resources.external_notification
import com.ntsocial.meshlink.core.resources.ic_alt_route
import com.ntsocial.meshlink.core.resources.ic_cloud
import com.ntsocial.meshlink.core.resources.ic_data_usage
import com.ntsocial.meshlink.core.resources.ic_group
import com.ntsocial.meshlink.core.resources.ic_light_mode
import com.ntsocial.meshlink.core.resources.ic_message
import com.ntsocial.meshlink.core.resources.ic_notifications
import com.ntsocial.meshlink.core.resources.ic_perm_scan_wifi
import com.ntsocial.meshlink.core.resources.ic_sensors
import com.ntsocial.meshlink.core.resources.ic_settings_remote
import com.ntsocial.meshlink.core.resources.ic_speed
import com.ntsocial.meshlink.core.resources.ic_terminal
import com.ntsocial.meshlink.core.resources.ic_usb
import com.ntsocial.meshlink.core.resources.ic_volume_up
import com.ntsocial.meshlink.core.resources.mqtt
import com.ntsocial.meshlink.core.resources.neighbor_info
import com.ntsocial.meshlink.core.resources.paxcounter
import com.ntsocial.meshlink.core.resources.range_test
import com.ntsocial.meshlink.core.resources.remote_hardware
import com.ntsocial.meshlink.core.resources.serial
import com.ntsocial.meshlink.core.resources.status_message
import com.ntsocial.meshlink.core.resources.store_forward
import com.ntsocial.meshlink.core.resources.tak
import com.ntsocial.meshlink.core.resources.telemetry
import com.ntsocial.meshlink.core.resources.traffic_management
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata

enum class ModuleRoute(
    val title: StringResource,
    val route: Route,
    val icon: DrawableResource? = null,
    val type: Int = 0,
    val isSupported: (Capabilities) -> Boolean = { true },
    val isApplicable: (Config.DeviceConfig.Role?) -> Boolean = { true },
) {
    MQTT(Res.string.mqtt, SettingsRoute.MQTT, Res.drawable.ic_cloud, AdminMessage.ModuleConfigType.MQTT_CONFIG.value),
    SERIAL(
        Res.string.serial,
        SettingsRoute.Serial,
        Res.drawable.ic_usb,
        AdminMessage.ModuleConfigType.SERIAL_CONFIG.value,
    ),
    EXT_NOTIFICATION(
        Res.string.external_notification,
        SettingsRoute.ExtNotification,
        Res.drawable.ic_notifications,
        AdminMessage.ModuleConfigType.EXTNOTIF_CONFIG.value,
    ),
    STORE_FORWARD(
        Res.string.store_forward,
        SettingsRoute.StoreForward,
        Res.drawable.ic_terminal,
        AdminMessage.ModuleConfigType.STOREFORWARD_CONFIG.value,
    ),
    RANGE_TEST(
        Res.string.range_test,
        SettingsRoute.RangeTest,
        Res.drawable.ic_speed,
        AdminMessage.ModuleConfigType.RANGETEST_CONFIG.value,
    ),
    TELEMETRY(
        Res.string.telemetry,
        SettingsRoute.Telemetry,
        Res.drawable.ic_data_usage,
        AdminMessage.ModuleConfigType.TELEMETRY_CONFIG.value,
    ),
    CANNED_MESSAGE(
        Res.string.canned_message,
        SettingsRoute.CannedMessage,
        Res.drawable.ic_message,
        AdminMessage.ModuleConfigType.CANNEDMSG_CONFIG.value,
    ),
    AUDIO(
        Res.string.audio,
        SettingsRoute.Audio,
        Res.drawable.ic_volume_up,
        AdminMessage.ModuleConfigType.AUDIO_CONFIG.value,
    ),
    REMOTE_HARDWARE(
        Res.string.remote_hardware,
        SettingsRoute.RemoteHardware,
        Res.drawable.ic_settings_remote,
        AdminMessage.ModuleConfigType.REMOTEHARDWARE_CONFIG.value,
    ),
    NEIGHBOR_INFO(
        Res.string.neighbor_info,
        SettingsRoute.NeighborInfo,
        Res.drawable.ic_group,
        AdminMessage.ModuleConfigType.NEIGHBORINFO_CONFIG.value,
    ),
    AMBIENT_LIGHTING(
        Res.string.ambient_lighting,
        SettingsRoute.AmbientLighting,
        Res.drawable.ic_light_mode,
        AdminMessage.ModuleConfigType.AMBIENTLIGHTING_CONFIG.value,
    ),
    DETECTION_SENSOR(
        Res.string.detection_sensor,
        SettingsRoute.DetectionSensor,
        Res.drawable.ic_sensors,
        AdminMessage.ModuleConfigType.DETECTIONSENSOR_CONFIG.value,
    ),
    PAXCOUNTER(
        Res.string.paxcounter,
        SettingsRoute.Paxcounter,
        Res.drawable.ic_perm_scan_wifi,
        AdminMessage.ModuleConfigType.PAXCOUNTER_CONFIG.value,
    ),
    STATUS_MESSAGE(
        Res.string.status_message,
        SettingsRoute.StatusMessage,
        Res.drawable.ic_message,
        AdminMessage.ModuleConfigType.STATUSMESSAGE_CONFIG.value,
        isSupported = { it.supportsStatusMessage },
    ),
    TRAFFIC_MANAGEMENT(
        Res.string.traffic_management,
        SettingsRoute.TrafficManagement,
        Res.drawable.ic_alt_route,
        AdminMessage.ModuleConfigType.TRAFFICMANAGEMENT_CONFIG.value,
        isSupported = { it.supportsTrafficManagementConfig },
    ),
    TAK(
        Res.string.tak,
        SettingsRoute.TAK,
        Res.drawable.ic_group,
        AdminMessage.ModuleConfigType.TAK_CONFIG.value,
        isSupported = { it.supportsTakConfig },
        isApplicable = { it == Config.DeviceConfig.Role.TAK || it == Config.DeviceConfig.Role.TAK_TRACKER },
    ),
    ;

    val bitfield: Int
        get() =
            when (this) {
                MQTT -> 0x0001

                SERIAL -> 0x0002

                EXT_NOTIFICATION -> 0x0004

                STORE_FORWARD -> 0x0008

                RANGE_TEST -> 0x0010

                TELEMETRY -> 0x0020

                CANNED_MESSAGE -> 0x0040

                AUDIO -> 0x0080

                REMOTE_HARDWARE -> 0x0100

                NEIGHBOR_INFO -> 0x0200

                AMBIENT_LIGHTING -> 0x0400

                DETECTION_SENSOR -> 0x0800

                PAXCOUNTER -> 0x1000

                STATUS_MESSAGE -> 0x0000

                // Not excludable yet
                TRAFFIC_MANAGEMENT -> 0x0000

                // Not excludable yet
                TAK -> 0x0000 // Not excludable yet
            }

    companion object {
        fun filterExcludedFrom(metadata: DeviceMetadata?, role: Config.DeviceConfig.Role?): List<ModuleRoute> {
            val capabilities = Capabilities(metadata?.firmware_version)
            return entries.filter {
                val excludedModules = metadata?.excluded_modules ?: 0
                val isExcluded = (excludedModules and it.bitfield) != 0
                !isExcluded && it.isSupported(capabilities) && it.isApplicable(role)
            }
        }
    }
}
