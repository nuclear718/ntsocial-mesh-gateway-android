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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment

private val NtsocialIndigo = Color(0xFF5B63EB)
private val NtsocialIndigoStrong = Color(0xFF3730A3)
private val NtsocialEmerald = Color(0xFF10B981)
private val NtsocialAmber = Color(0xFFF59E0B)
private val NtsocialBackground = Color(0xFF0E1420)
private val NtsocialPanel = Color(0xE6161E2C)
private val NtsocialRaisedPanel = Color(0xF0212B3B)
private val NtsocialText = Color(0xFFF3F5F9)
private val NtsocialMutedText = Color(0xFFA9B2C1)
private val NtsocialOutline = Color(0x24FFFFFF)

private val NtsocialWindowsDarkColorScheme =
    darkColorScheme(
        primary = NtsocialIndigo,
        onPrimary = Color.White,
        primaryContainer = NtsocialIndigoStrong,
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = NtsocialEmerald,
        onSecondary = Color(0xFF03251B),
        secondaryContainer = Color(0xFF064E3B),
        onSecondaryContainer = Color(0xFFD1FAE5),
        tertiary = NtsocialAmber,
        onTertiary = Color(0xFF2D1B00),
        tertiaryContainer = Color(0xFF78350F),
        onTertiaryContainer = Color(0xFFFEF3C7),
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2),
        background = NtsocialBackground,
        onBackground = NtsocialText,
        surface = NtsocialPanel,
        onSurface = NtsocialText,
        surfaceVariant = NtsocialRaisedPanel,
        onSurfaceVariant = NtsocialMutedText,
        outline = NtsocialOutline,
        outlineVariant = Color(0x18FFFFFF),
        scrim = Color(0xB8000000),
        inverseSurface = Color(0xFFF3F5F9),
        inverseOnSurface = NtsocialBackground,
        inversePrimary = NtsocialIndigoStrong,
        surfaceDim = Color(0xE60E1420),
        surfaceBright = NtsocialRaisedPanel,
        surfaceContainerLowest = Color(0xD90E1420),
        surfaceContainerLow = Color(0xE60C1220),
        surfaceContainer = NtsocialPanel,
        surfaceContainerHigh = Color(0xEB1B2433),
        surfaceContainerHighest = NtsocialRaisedPanel,
    )

private val NtsocialWindowsLightColorScheme =
    lightColorScheme(
        primary = NtsocialIndigo,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3E5FF),
        onPrimaryContainer = Color(0xFF181A60),
        secondary = Color(0xFF087F5B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1FAE5),
        onSecondaryContainer = Color(0xFF064E3B),
        tertiary = Color(0xFFB45309),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFEF3C7),
        onTertiaryContainer = Color(0xFF78350F),
        error = Color(0xFFB91C1C),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
        background = Color(0xFFF5F7FB),
        onBackground = Color(0xFF111827),
        surface = Color(0xF0FFFFFF),
        onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xF2F3F4FA),
        onSurfaceVariant = Color(0xFF4B5563),
        outline = Color(0x385B63EB),
        outlineVariant = Color(0x245B63EB),
        scrim = Color(0x520E1420),
        inverseSurface = Color(0xFF212B3B),
        inverseOnSurface = Color(0xFFF9FAFB),
        inversePrimary = Color(0xFFAAB0FF),
        surfaceDim = Color(0xEBE6E9F1),
        surfaceBright = Color(0xF7FFFFFF),
        surfaceContainerLowest = Color(0xF7FFFFFF),
        surfaceContainerLow = Color(0xF2FFFFFF),
        surfaceContainer = Color(0xF0F9FAFD),
        surfaceContainerHigh = Color(0xF2F2F4F9),
        surfaceContainerHighest = Color(0xF5ECEFF5),
    )

internal fun ntsocialWindowsColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) NtsocialWindowsDarkColorScheme else NtsocialWindowsLightColorScheme

internal val NtsocialWindowsTypography: Typography by lazy { createWindowsTypography() }

@OptIn(ExperimentalTextApi::class)
private fun createWindowsTypography(): Typography {
    val installedFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    val displayFont = installedFonts.preferredFontFamily("Segoe UI Variable Display", "Segoe UI Variable", "Segoe UI")
    val bodyFont = installedFonts.preferredFontFamily("Segoe UI Variable Text", "Segoe UI Variable", "Segoe UI")
    val monospaceFont = installedFonts.preferredFontFamily("Cascadia Mono", "Cascadia Code", "Consolas")
    val base = Typography()

    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = displayFont),
        displayMedium = base.displayMedium.copy(fontFamily = displayFont),
        displaySmall = base.displaySmall.copy(fontFamily = displayFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = displayFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = displayFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = displayFont),
        titleLarge = base.titleLarge.copy(fontFamily = displayFont),
        titleMedium = base.titleMedium.copy(fontFamily = bodyFont),
        titleSmall = base.titleSmall.copy(fontFamily = monospaceFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFont),
        bodySmall = base.bodySmall.copy(fontFamily = monospaceFont),
        labelLarge = base.labelLarge.copy(fontFamily = monospaceFont),
        labelMedium = base.labelMedium.copy(fontFamily = monospaceFont),
        labelSmall = base.labelSmall.copy(fontFamily = monospaceFont),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun Set<String>.preferredFontFamily(vararg preferences: String): FontFamily =
    preferences.firstOrNull { contains(it) }?.let(::FontFamily) ?: FontFamily.Default
