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
package com.ntsocial.meshlink.core.model

import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.event_welcome_burning_man
import com.ntsocial.meshlink.core.resources.event_welcome_defcon
import com.ntsocial.meshlink.core.resources.event_welcome_hamvention
import com.ntsocial.meshlink.core.resources.event_welcome_open_sauce
import com.ntsocial.meshlink.core.resources.img_event_hamvention
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.proto.FirmwareEdition

/**
 * Display metadata for event-specific firmware editions. Maps a [FirmwareEdition] proto enum to the resources needed to
 * render event branding in the UI (toolbar icon, welcome message, etc.).
 */
data class EventEdition(val name: String, val iconRes: DrawableResource?, val welcomeMessageRes: StringResource)

/**
 * Maps a [FirmwareEdition] to its [EventEdition] display metadata, or `null` if the edition is not an event (e.g.,
 * VANILLA, SMART_CITIZEN) or is unrecognized.
 */
fun FirmwareEdition.toEventEdition(): EventEdition? = when (this) {
    FirmwareEdition.HAMVENTION ->
        EventEdition(
            name = "Hamvention",
            iconRes = Res.drawable.img_event_hamvention,
            welcomeMessageRes = Res.string.event_welcome_hamvention,
        )

    FirmwareEdition.OPEN_SAUCE ->
        EventEdition(name = "Open Sauce", iconRes = null, welcomeMessageRes = Res.string.event_welcome_open_sauce)

    FirmwareEdition.DEFCON ->
        EventEdition(name = "DEFCON", iconRes = null, welcomeMessageRes = Res.string.event_welcome_defcon)

    FirmwareEdition.BURNING_MAN ->
        EventEdition(name = "Burning Man", iconRes = null, welcomeMessageRes = Res.string.event_welcome_burning_man)

    else -> null
}

/** Returns `true` if this edition represents an event firmware (value >= 16, excluding DIY). */
fun FirmwareEdition.isEventEdition(): Boolean = toEventEdition() != null
