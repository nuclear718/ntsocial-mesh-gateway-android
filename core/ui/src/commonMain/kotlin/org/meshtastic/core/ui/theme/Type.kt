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
package org.meshtastic.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val baseTypography = Typography()

val AppTypography =
    Typography(
        displayLarge = baseTypography.displayLarge,
        displayMedium = baseTypography.displayMedium,
        displaySmall = baseTypography.displaySmall,
        headlineLarge = baseTypography.headlineLarge,
        headlineMedium = baseTypography.headlineMedium,
        headlineSmall = baseTypography.headlineSmall,
        titleLarge = baseTypography.titleLarge,
        titleMedium = baseTypography.titleMedium,
        titleSmall = baseTypography.titleSmall.copy(fontFamily = FontFamily.Monospace),
        bodyLarge = baseTypography.bodyLarge,
        bodyMedium = baseTypography.bodyMedium,
        bodySmall = baseTypography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = FontFamily.Monospace),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = FontFamily.Monospace),
    )
