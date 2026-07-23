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
package com.ntsocial.meshlink.desktop.branding

import com.ntsocial.meshlink.desktop.notification.DesktopOS

internal const val WINDOWS_SPLASH_SEGMENT_DURATION_MILLIS = 1_000L
internal const val WINDOWS_SPLASH_DURATION_MILLIS = 3 * WINDOWS_SPLASH_SEGMENT_DURATION_MILLIS

/** Product identity and host resources selected without changing non-Windows desktop metadata or visuals. */
internal data class DesktopBranding(
    val productName: String,
    val applicationId: String,
    val notificationAppId: String,
    val windowIconResource: String,
    val trayIconResource: String?,
    val appBarIconResource: String?,
    val isNtsocialWindows: Boolean,
)

internal val NtsocialWindowsBranding =
    DesktopBranding(
        productName = "NTsocial MeshLink",
        applicationId = "com.ntsocial.meshlink.desktop",
        notificationAppId = "NTsocial MeshLink",
        windowIconResource = "ntsocial_windows_butterfly_512.png",
        trayIconResource = "ntsocial_windows_butterfly_24.png",
        appBarIconResource = "ntsocial_windows_butterfly_48.png",
        isNtsocialWindows = true,
    )

private val MeshtasticDesktopBranding =
    DesktopBranding(
        productName = "Meshtastic Desktop",
        applicationId = "com.ntsocial.meshlink.desktop",
        notificationAppId = "Meshtastic",
        windowIconResource = "tray_icon_black.svg",
        trayIconResource = null,
        appBarIconResource = null,
        isNtsocialWindows = false,
    )

internal fun desktopBrandingFor(os: DesktopOS): DesktopBranding =
    if (os == DesktopOS.Windows) NtsocialWindowsBranding else MeshtasticDesktopBranding

/** Visual values for the fixed three-second Windows cold-start sequence. */
internal data class WindowsSplashVisualState(
    val backgroundAlpha: Float,
    val brandAlpha: Float,
    val overlayAlpha: Float,
    val isFinished: Boolean,
)

internal fun windowsSplashVisualState(elapsedMillis: Long): WindowsSplashVisualState {
    val elapsed = elapsedMillis.coerceAtLeast(0L)
    val firstBoundary = WINDOWS_SPLASH_SEGMENT_DURATION_MILLIS
    val secondBoundary = firstBoundary * 2

    return when {
        elapsed < firstBoundary ->
            WindowsSplashVisualState(
                backgroundAlpha = segmentProgress(elapsed),
                brandAlpha = 0F,
                overlayAlpha = 1F,
                isFinished = false,
            )

        elapsed < secondBoundary ->
            WindowsSplashVisualState(
                backgroundAlpha = 1F,
                brandAlpha = segmentProgress(elapsed - firstBoundary),
                overlayAlpha = 1F,
                isFinished = false,
            )

        elapsed < WINDOWS_SPLASH_DURATION_MILLIS ->
            WindowsSplashVisualState(
                backgroundAlpha = 1F,
                brandAlpha = 1F,
                overlayAlpha = 1F - segmentProgress(elapsed - secondBoundary),
                isFinished = false,
            )

        else -> WindowsSplashVisualState(backgroundAlpha = 1F, brandAlpha = 1F, overlayAlpha = 0F, isFinished = true)
    }
}

private fun segmentProgress(elapsedMillis: Long): Float =
    (elapsedMillis.toFloat() / WINDOWS_SPLASH_SEGMENT_DURATION_MILLIS).coerceIn(0F, 1F)

/** Consumes the Windows splash once for one process-level application composition. */
internal class ColdLaunchSplashPlayback {
    private var consumed = false

    fun shouldPlay(os: DesktopOS): Boolean {
        if (os != DesktopOS.Windows || consumed) return false
        consumed = true
        return true
    }
}
