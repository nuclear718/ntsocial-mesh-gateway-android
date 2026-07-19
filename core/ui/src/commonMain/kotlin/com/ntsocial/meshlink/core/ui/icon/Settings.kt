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
import com.ntsocial.meshlink.core.resources.ic_abc
import com.ntsocial.meshlink.core.resources.ic_admin_panel_settings
import com.ntsocial.meshlink.core.resources.ic_app_settings_alt
import com.ntsocial.meshlink.core.resources.ic_bug_report
import com.ntsocial.meshlink.core.resources.ic_cleaning_services
import com.ntsocial.meshlink.core.resources.ic_data_usage
import com.ntsocial.meshlink.core.resources.ic_format_paint
import com.ntsocial.meshlink.core.resources.ic_language
import com.ntsocial.meshlink.core.resources.ic_list
import com.ntsocial.meshlink.core.resources.ic_notifications
import com.ntsocial.meshlink.core.resources.ic_perm_scan_wifi
import com.ntsocial.meshlink.core.resources.ic_sensors
import com.ntsocial.meshlink.core.resources.ic_settings
import com.ntsocial.meshlink.core.resources.ic_settings_remote
import com.ntsocial.meshlink.core.resources.ic_storage
import com.ntsocial.meshlink.core.resources.ic_waving_hand
import org.jetbrains.compose.resources.vectorResource

// Config route icons
val MeshtasticIcons.AdminPanelSettings: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_admin_panel_settings)
val MeshtasticIcons.AppSettingsAlt: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_app_settings_alt)
val MeshtasticIcons.BugReport: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bug_report)
val MeshtasticIcons.CleaningServices: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cleaning_services)
val MeshtasticIcons.FormatPaint: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_paint)
val MeshtasticIcons.Language: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_language)
val MeshtasticIcons.WavingHand: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_waving_hand)
val MeshtasticIcons.Abc: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_abc)
val MeshtasticIcons.Settings: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_settings)
val MeshtasticIcons.ConfigChannels: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_list)
val MeshtasticIcons.Notifications: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_notifications)
val MeshtasticIcons.DataUsage: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_data_usage)
val MeshtasticIcons.PermScanWifi: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_perm_scan_wifi)
val MeshtasticIcons.DetectionSensor: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_sensors)
val MeshtasticIcons.SettingsRemote: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_settings_remote)
val MeshtasticIcons.Storage: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_storage)
