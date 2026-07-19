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
package com.ntsocial.meshlink.feature.meshcore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.meshcore.MeshCoreContact
import com.ntsocial.meshlink.core.meshcore.MeshCoreContactType
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channels
import com.ntsocial.meshlink.core.resources.ic_chat_bubble_outline
import com.ntsocial.meshlink.core.resources.ic_hub
import com.ntsocial.meshlink.core.resources.ic_person
import com.ntsocial.meshlink.core.resources.ic_router
import com.ntsocial.meshlink.core.resources.ic_sensors
import com.ntsocial.meshlink.core.resources.meshcore_channel_index
import com.ntsocial.meshlink.core.resources.meshcore_contact_type_chat
import com.ntsocial.meshlink.core.resources.meshcore_contact_type_repeater
import com.ntsocial.meshlink.core.resources.meshcore_contact_type_room
import com.ntsocial.meshlink.core.resources.meshcore_contact_type_sensor
import com.ntsocial.meshlink.core.resources.meshcore_contact_type_unknown
import com.ntsocial.meshlink.core.resources.meshcore_contacts
import com.ntsocial.meshlink.core.resources.meshcore_no_channels
import com.ntsocial.meshlink.core.resources.meshcore_no_contacts
import com.ntsocial.meshlink.core.ui.component.AdaptiveTwoPane
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MeshCoreDirectoryPanel(state: MeshCoreUiState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        AdaptiveTwoPane(
            first = {
                MeshCoreSectionCard(title = stringResource(Res.string.meshcore_contacts)) {
                    if (state.contacts.isEmpty()) {
                        MeshCoreGroupEmpty(text = stringResource(Res.string.meshcore_no_contacts))
                    } else {
                        state.contacts.forEachIndexed { index, contact ->
                            MeshCoreContactRow(contact)
                            if (index != state.contacts.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                }
            },
            second = {
                MeshCoreSectionCard(title = stringResource(Res.string.channels)) {
                    val channels = state.channels.filter { it.name.isNotBlank() }
                    if (channels.isEmpty()) {
                        MeshCoreGroupEmpty(text = stringResource(Res.string.meshcore_no_channels))
                    } else {
                        channels.forEachIndexed { index, channel ->
                            MeshCoreListRow(
                                icon = Res.drawable.ic_chat_bubble_outline,
                                title = "#${channel.name.removePrefix("#")}",
                                subtitle = stringResource(Res.string.meshcore_channel_index, channel.index),
                            )
                            if (index != channels.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun MeshCoreContactRow(contact: MeshCoreContact) {
    MeshCoreListRow(
        icon = contact.type.iconResource(),
        title = contact.name.ifBlank { stringResource(Res.string.meshcore_contact_type_unknown) },
        subtitle = stringResource(contact.type.labelResource()),
    )
}

@Composable
private fun MeshCoreGroupEmpty(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun MeshCoreContactType.labelResource() = when (this) {
    MeshCoreContactType.CHAT -> Res.string.meshcore_contact_type_chat
    MeshCoreContactType.REPEATER -> Res.string.meshcore_contact_type_repeater
    MeshCoreContactType.ROOM_SERVER -> Res.string.meshcore_contact_type_room
    MeshCoreContactType.SENSOR -> Res.string.meshcore_contact_type_sensor
    MeshCoreContactType.UNKNOWN -> Res.string.meshcore_contact_type_unknown
}

private fun MeshCoreContactType.iconResource() = when (this) {
    MeshCoreContactType.CHAT -> Res.drawable.ic_person
    MeshCoreContactType.REPEATER -> Res.drawable.ic_router
    MeshCoreContactType.ROOM_SERVER -> Res.drawable.ic_hub
    MeshCoreContactType.SENSOR -> Res.drawable.ic_sensors
    MeshCoreContactType.UNKNOWN -> Res.drawable.ic_person
}
