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
package com.ntsocial.meshlink.core.model.util

import com.ntsocial.meshlink.core.common.util.nowMillis
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle

private const val MILLISECONDS_PER_SECOND = 1_000.0
private const val MILLISECONDS_PER_DAY = 24L * 60L * 60L * 1_000L
private const val APPLE_REFERENCE_DATE_UNIX_SECONDS = 978_307_200.0

/** Uses the user's current iOS locale, calendar, time zone, and 12/24-hour preference. */
actual fun getShortDateTime(time: Long): String {
    val date =
        NSDate(timeIntervalSinceReferenceDate = time / MILLISECONDS_PER_SECOND - APPLE_REFERENCE_DATE_UNIX_SECONDS)
    val isWithin24Hours = nowMillis - time <= MILLISECONDS_PER_DAY

    return NSDateFormatter().run {
        dateStyle = if (isWithin24Hours) NSDateFormatterNoStyle else NSDateFormatterShortStyle
        timeStyle = NSDateFormatterShortStyle
        stringFromDate(date)
    }
}
