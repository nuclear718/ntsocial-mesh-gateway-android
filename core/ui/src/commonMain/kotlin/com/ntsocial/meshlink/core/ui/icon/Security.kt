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
package com.ntsocial.meshlink.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.ic_key_off
import com.ntsocial.meshlink.core.resources.ic_lock
import com.ntsocial.meshlink.core.resources.ic_lock_open
import com.ntsocial.meshlink.core.resources.ic_security
import com.ntsocial.meshlink.core.resources.ic_verified
import org.jetbrains.compose.resources.vectorResource

val MeshtasticIcons.Verified: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_verified)
val MeshtasticIcons.Lock: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_lock)
val MeshtasticIcons.LockOpen: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_lock_open)
val MeshtasticIcons.KeyOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_key_off)
val MeshtasticIcons.SecurityShield: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_security)
