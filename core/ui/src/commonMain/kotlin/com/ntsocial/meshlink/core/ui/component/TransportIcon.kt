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
package com.ntsocial.meshlink.core.ui.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.internal
import com.ntsocial.meshlink.core.resources.via_api
import com.ntsocial.meshlink.core.resources.via_mqtt
import com.ntsocial.meshlink.core.resources.via_udp
import com.ntsocial.meshlink.core.ui.icon.Api
import com.ntsocial.meshlink.core.ui.icon.Device
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.MqttConnected
import com.ntsocial.meshlink.core.ui.icon.Udp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.MeshPacket

@Composable
fun TransportIcon(transport: Int, viaMqtt: Boolean, modifier: Modifier = Modifier) {
    val (icon, description) =
        when {
            viaMqtt || transport == MeshPacket.TransportMechanism.TRANSPORT_MQTT.value ->
                MeshtasticIcons.MqttConnected to stringResource(Res.string.via_mqtt)

            transport == MeshPacket.TransportMechanism.TRANSPORT_MULTICAST_UDP.value ->
                MeshtasticIcons.Udp to stringResource(Res.string.via_udp)

            transport == MeshPacket.TransportMechanism.TRANSPORT_API.value ->
                MeshtasticIcons.Api to stringResource(Res.string.via_api)

            transport == MeshPacket.TransportMechanism.TRANSPORT_INTERNAL.value ->
                MeshtasticIcons.Device to stringResource(Res.string.internal)

            else -> return
        }
    Icon(icon, contentDescription = description, modifier = modifier, tint = Color.White)
}
