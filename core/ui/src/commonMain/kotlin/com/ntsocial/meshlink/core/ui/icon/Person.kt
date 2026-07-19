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
import com.ntsocial.meshlink.core.resources.ic_account_circle
import com.ntsocial.meshlink.core.resources.ic_group
import com.ntsocial.meshlink.core.resources.ic_groups
import com.ntsocial.meshlink.core.resources.ic_person
import com.ntsocial.meshlink.core.resources.ic_person_add
import com.ntsocial.meshlink.core.resources.ic_person_off
import com.ntsocial.meshlink.core.resources.ic_person_search
import org.jetbrains.compose.resources.vectorResource

val MeshtasticIcons.PersonOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_person_off)
val MeshtasticIcons.Group: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_group)
val MeshtasticIcons.AccountCircle: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_account_circle)
val MeshtasticIcons.PersonSearch: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_person_search)

val MeshtasticIcons.PersonAdd: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_person_add)
val MeshtasticIcons.Person: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_person)
val MeshtasticIcons.Groups: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_groups)
val MeshtasticIcons.PeopleCount: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_group)
