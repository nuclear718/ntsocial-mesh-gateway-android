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
package com.ntsocial.meshlink.feature.settings.component

import androidx.compose.runtime.Composable
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.app_notifications
import com.ntsocial.meshlink.core.resources.meshtastic_low_battery_notifications
import com.ntsocial.meshlink.core.resources.meshtastic_messages_notifications
import com.ntsocial.meshlink.core.resources.meshtastic_new_nodes_notifications
import com.ntsocial.meshlink.core.ui.component.SwitchListItem
import com.ntsocial.meshlink.core.ui.icon.BatteryAlert
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Message
import com.ntsocial.meshlink.core.ui.icon.PersonAdd
import org.jetbrains.compose.resources.stringResource

/**
 * Notification settings section with in-app toggles. Primarily used on platforms without system notification channels.
 */
@Composable
fun NotificationSection(
    messagesEnabled: Boolean,
    onToggleMessages: (Boolean) -> Unit,
    nodeEventsEnabled: Boolean,
    onToggleNodeEvents: (Boolean) -> Unit,
    lowBatteryEnabled: Boolean,
    onToggleLowBattery: (Boolean) -> Unit,
) {
    ExpressiveSection(title = stringResource(Res.string.app_notifications)) {
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_messages_notifications),
            leadingIcon = MeshtasticIcons.Message,
            checked = messagesEnabled,
            onClick = { onToggleMessages(!messagesEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_new_nodes_notifications),
            leadingIcon = MeshtasticIcons.PersonAdd,
            checked = nodeEventsEnabled,
            onClick = { onToggleNodeEvents(!nodeEventsEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_low_battery_notifications),
            leadingIcon = MeshtasticIcons.BatteryAlert,
            checked = lowBatteryEnabled,
            onClick = { onToggleLowBattery(!lowBatteryEnabled) },
        )
    }
}
