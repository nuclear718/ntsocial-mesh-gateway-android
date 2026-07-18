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
package com.ntsocial.meshlink.core.ui.util

import com.ntsocial.meshlink.core.model.ChannelOption
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.label_lite_fast
import com.ntsocial.meshlink.core.resources.label_lite_slow
import com.ntsocial.meshlink.core.resources.label_long_fast
import com.ntsocial.meshlink.core.resources.label_long_moderate
import com.ntsocial.meshlink.core.resources.label_long_slow
import com.ntsocial.meshlink.core.resources.label_long_turbo
import com.ntsocial.meshlink.core.resources.label_medium_fast
import com.ntsocial.meshlink.core.resources.label_medium_slow
import com.ntsocial.meshlink.core.resources.label_narrow_fast
import com.ntsocial.meshlink.core.resources.label_narrow_slow
import com.ntsocial.meshlink.core.resources.label_short_fast
import com.ntsocial.meshlink.core.resources.label_short_slow
import com.ntsocial.meshlink.core.resources.label_short_turbo
import com.ntsocial.meshlink.core.resources.label_very_long_slow
import org.jetbrains.compose.resources.StringResource

val ChannelOption.labelRes: StringResource
    get() =
        when (this) {
            ChannelOption.VERY_LONG_SLOW -> Res.string.label_very_long_slow
            ChannelOption.LONG_TURBO -> Res.string.label_long_turbo
            ChannelOption.LONG_FAST -> Res.string.label_long_fast
            ChannelOption.LONG_MODERATE -> Res.string.label_long_moderate
            ChannelOption.LONG_SLOW -> Res.string.label_long_slow
            ChannelOption.MEDIUM_FAST -> Res.string.label_medium_fast
            ChannelOption.MEDIUM_SLOW -> Res.string.label_medium_slow
            ChannelOption.SHORT_FAST -> Res.string.label_short_fast
            ChannelOption.SHORT_SLOW -> Res.string.label_short_slow
            ChannelOption.SHORT_TURBO -> Res.string.label_short_turbo
            ChannelOption.LITE_FAST -> Res.string.label_lite_fast
            ChannelOption.LITE_SLOW -> Res.string.label_lite_slow
            ChannelOption.NARROW_FAST -> Res.string.label_narrow_fast
            ChannelOption.NARROW_SLOW -> Res.string.label_narrow_slow
        }
