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
package com.ntsocial.meshlink.feature.messaging.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.message_delivery_status
import com.ntsocial.meshlink.core.ui.icon.Acknowledged
import com.ntsocial.meshlink.core.ui.icon.AddLink
import com.ntsocial.meshlink.core.ui.icon.CloudUpload
import com.ntsocial.meshlink.core.ui.icon.LinkIcon
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.MessageEnroute
import com.ntsocial.meshlink.core.ui.icon.MessageError
import com.ntsocial.meshlink.core.ui.icon.MqttDelivered
import com.ntsocial.meshlink.core.ui.icon.Warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun MessageStatusIcon(status: MessageStatus, modifier: Modifier = Modifier) {
    val icon =
        when (status) {
            MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
            MessageStatus.QUEUED -> MeshtasticIcons.CloudUpload
            MessageStatus.DELIVERED -> MeshtasticIcons.MqttDelivered
            MessageStatus.SFPP_ROUTING -> MeshtasticIcons.AddLink
            MessageStatus.SFPP_CONFIRMED -> MeshtasticIcons.LinkIcon
            MessageStatus.ENROUTE -> MeshtasticIcons.MessageEnroute
            MessageStatus.ERROR -> MeshtasticIcons.MessageError
            else -> MeshtasticIcons.Warning
        }
    Icon(
        modifier = modifier,
        imageVector = icon,
        contentDescription = stringResource(Res.string.message_delivery_status),
    )
}
