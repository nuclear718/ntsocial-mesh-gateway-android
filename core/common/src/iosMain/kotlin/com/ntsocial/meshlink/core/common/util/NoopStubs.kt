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
package com.ntsocial.meshlink.core.common.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyleNamed
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleShort
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.usesMetricSystem

actual object BuildUtils {
    actual val isEmulator: Boolean = false
    actual val sdkInt: Int = 0
}

/** Uses Foundation formatters so shared node/message UI follows the user's iOS locale and 12/24-hour setting. */
actual object DateFormatter {
    private val shortTimeFormatter = dateFormatter(NSDateFormatterNoStyle, NSDateFormatterShortStyle)
    private val mediumTimeFormatter = dateFormatter(NSDateFormatterNoStyle, NSDateFormatterMediumStyle)
    private val shortDateFormatter = dateFormatter(NSDateFormatterShortStyle, NSDateFormatterNoStyle)
    private val shortDateTimeFormatter = dateFormatter(NSDateFormatterShortStyle, NSDateFormatterShortStyle)
    private val relativeFormatter =
        NSRelativeDateTimeFormatter().apply {
            dateTimeStyle = NSRelativeDateTimeFormatterStyleNamed
            unitsStyle = NSRelativeDateTimeFormatterUnitsStyleShort
        }

    actual fun formatRelativeTime(timestampMillis: Long): String =
        relativeFormatter.localizedStringForDate(timestampMillis.toNSDate(), relativeToDate = NSDate())

    actual fun formatDateTime(timestampMillis: Long): String = shortDateTimeFormatter.format(timestampMillis)

    actual fun formatShortDate(timestampMillis: Long): String =
        if (nowMillis - timestampMillis <= MILLISECONDS_PER_DAY) {
            shortTimeFormatter.format(timestampMillis)
        } else {
            shortDateFormatter.format(timestampMillis)
        }

    actual fun formatTime(timestampMillis: Long): String = shortTimeFormatter.format(timestampMillis)

    actual fun formatTimeWithSeconds(timestampMillis: Long): String = mediumTimeFormatter.format(timestampMillis)

    actual fun formatDate(timestampMillis: Long): String = shortDateFormatter.format(timestampMillis)

    actual fun formatDateTimeShort(timestampMillis: Long): String = shortDateTimeFormatter.format(timestampMillis)

    private fun NSDateFormatter.format(timestampMillis: Long): String = stringFromDate(timestampMillis.toNSDate())
}

actual fun getSystemMeasurementSystem(): MeasurementSystem =
    if (NSLocale.currentLocale.usesMetricSystem) MeasurementSystem.METRIC else MeasurementSystem.IMPERIAL

actual fun currentRegionCode(): String = NSLocale.currentLocale.countryCode.orEmpty()

actual fun String?.isValidAddress(): Boolean {
    val value = this?.trim()
    return when {
        value.isNullOrEmpty() -> false
        value == LOCALHOST -> true
        IPV4_PATTERN.matches(value) -> value.split('.').all { segment -> segment.toIntOrNull() in 0..MAX_IPV4_SEGMENT }
        value.contains(':') -> IPV6_PATTERN.matches(value)
        else -> DOMAIN_PATTERN.matches(value)
    }
}

actual interface CommonParcelable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class CommonParcelize actual constructor()

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
actual annotation class CommonIgnoredOnParcel actual constructor()

actual interface CommonParceler<T> {
    actual fun create(parcel: CommonParcel): T

    actual fun T.write(parcel: CommonParcel, flags: Int)
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
actual annotation class CommonTypeParceler<T, P : CommonParceler<in T>> actual constructor()

actual class CommonParcel {
    actual fun readString(): String? = null

    actual fun readInt(): Int = 0

    actual fun readLong(): Long = 0L

    actual fun readFloat(): Float = 0.0f

    actual fun createByteArray(): ByteArray? = null

    actual fun writeByteArray(b: ByteArray?) {}
}

private fun dateFormatter(dateStyle: ULong, timeStyle: ULong): NSDateFormatter = NSDateFormatter().apply {
    this.dateStyle = dateStyle
    this.timeStyle = timeStyle
}

private fun Long.toNSDate(): NSDate =
    NSDate(timeIntervalSinceReferenceDate = this / MILLISECONDS_PER_SECOND - APPLE_REFERENCE_DATE_UNIX_SECONDS)

private val IPV4_PATTERN = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}${'$'}")
private val IPV6_PATTERN = Regex("^[0-9A-Fa-f:]+${'$'}")
private val DOMAIN_PATTERN = Regex("^(?=.{1,253}${'$'})(?:(?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,63}${'$'}")

private const val MILLISECONDS_PER_SECOND = 1_000.0
private const val MILLISECONDS_PER_DAY = 86_400_000L
private const val APPLE_REFERENCE_DATE_UNIX_SECONDS = 978_307_200.0
private const val MAX_IPV4_SEGMENT = 255
private const val LOCALHOST = "localhost"
