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
import com.ntsocial.meshlink.core.resources.ic_add_link
import com.ntsocial.meshlink.core.resources.ic_chat_bubble_outline
import com.ntsocial.meshlink.core.resources.ic_fast_forward
import com.ntsocial.meshlink.core.resources.ic_filter_list
import com.ntsocial.meshlink.core.resources.ic_filter_list_off
import com.ntsocial.meshlink.core.resources.ic_format_quote
import com.ntsocial.meshlink.core.resources.ic_forum
import com.ntsocial.meshlink.core.resources.ic_link
import com.ntsocial.meshlink.core.resources.ic_message
import com.ntsocial.meshlink.core.resources.ic_visibility
import com.ntsocial.meshlink.core.resources.ic_visibility_off
import org.jetbrains.compose.resources.vectorResource

// Messaging UI icons
val MeshtasticIcons.ChatBubbleOutline: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_chat_bubble_outline)
val MeshtasticIcons.FormatQuote: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_quote)
val MeshtasticIcons.FilterList: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_filter_list)
val MeshtasticIcons.FilterListOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_filter_list_off)
val MeshtasticIcons.FastForward: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_fast_forward)
val MeshtasticIcons.Visibility: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_visibility)
val MeshtasticIcons.VisibilityOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_visibility_off)
val MeshtasticIcons.AddLink: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_add_link)
val MeshtasticIcons.LinkIcon: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_link)
val MeshtasticIcons.Message: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_message)
val MeshtasticIcons.Conversations: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_forum)
