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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// NTsocial Gateway brand aliases kept under existing names for source compatibility.
val MeshtasticGreen = Color(0xFF10B981)
val MeshtasticAlt = Color(0xFF4F46E5)

// NTsocial neutral scale.
object NeutralPalette {
    val N950 = Color(0xFF030712)
    val N900 = Color(0xFF111827)
    val N800 = Color(0xFF1F2937)
    val N700 = Color(0xFF374151)
    val N600 = Color(0xFF4B5563)
    val N500 = Color(0xFF6B7280)
    val N400 = Color(0xFF9CA3AF)
    val N300 = Color(0xFFD1D5DB)
    val N200 = Color(0xFFE5E7EB)
    val N100 = Color(0xFFF3F4F6)
    val N50 = Color(0xFFF9FAFB)
}

// NTsocial neutral variant scale.
object NeutralVariantPalette {
    val NV900 = Color(0xFF111827)
    val NV800 = Color(0xFF1F2937)
    val NV700 = Color(0xFF374151)
    val NV600 = Color(0xFF4B5563)
    val NV500 = Color(0xFF6B7280)
    val NV400 = Color(0xFF9CA3AF)
    val NV300 = Color(0xFFD1D5DB)
    val NV200 = Color(0xFFE5E7EB)
    val NV100 = Color(0xFFF3F4F6)
    val NV50 = Color(0xFFF9FAFB)
}

// NTsocial emerald scale.
object GreenPalette {
    val G950 = Color(0xFF022C22)
    val G900 = Color(0xFF064E3B)
    val G800 = Color(0xFF065F46)
    val G700 = Color(0xFF047857)
    val G600 = Color(0xFF059669)
    val G500 = Color(0xFF10B981)
    val G400 = Color(0xFF34D399)
    val G300 = Color(0xFF6EE7B7)
    val G200 = Color(0xFFA7F3D0)
    val G100 = Color(0xFFD1FAE5)
    val G50 = Color(0xFFECFDF5)
}

// NTsocial indigo scale. The BluePalette name is retained for source compatibility.
object BluePalette {
    val B950 = Color(0xFF1E1B4B)
    val B900 = Color(0xFF312E81)
    val B800 = Color(0xFF3730A3)
    val B700 = Color(0xFF4338CA)
    val B600 = Color(0xFF4F46E5)
    val B500 = Color(0xFF6366F1)
    val B400 = Color(0xFF818CF8)
    val B300 = Color(0xFFA5B4FC)
    val B200 = Color(0xFFC7D2FE)
    val B100 = Color(0xFFE0E7FF)
    val B50 = Color(0xFFEEF2FF)
}

// NTsocial error scale.
object ErrorPalette {
    val E900 = Color(0xFF7F1D1D)
    val E800 = Color(0xFF991B1B)
    val E700 = Color(0xFFB91C1C)
    val E600 = Color(0xFFDC2626)
    val E500 = Color(0xFFEF4444)
    val E400 = Color(0xFFF87171)
    val E300 = Color(0xFFFCA5A5)
    val E200 = Color(0xFFFECACA)
    val E100 = Color(0xFFFEE2E2)
}

// NTsocial semantic colors.
object SemanticColors {
    val Accent = Color(0xFF4F46E5)
    val AccentLight = Color(0xFFE0E7FF)
    val Info = Color(0xFF6366F1)
    val InfoLight = Color(0xFFEEF2FF)
    val Warning = Color(0xFFF59E0B)
    val WarningLight = Color(0xFFFFEDD5)
    val Error = Color(0xFFEF4444)
    val ErrorLight = Color(0xFFFEE2E2)
    val Success = Color(0xFF10B981)
    val SuccessLight = Color(0xFFD1FAE5)
}

val HyperlinkBlue = Color(0xFF4F46E5)
val AnnotationColor = Color(0xFF4F46E5)

object TracerouteColors {
    // High-contrast pair that stays legible on light/dark tiles and for most color-blind users.
    // Use partial alpha so polylines do not overpower markers/tiles.
    val OutgoingRoute = Color(0xCCE86A00)
    val ReturnRoute = Color(0xCC0081C7)
}

object IAQColors {
    val IAQExcellent = Color(0xFF00E400)
    val IAQGood = Color(0xFF92D050)
    val IAQLightlyPolluted = Color(0xFFFFFF00)
    val IAQModeratelyPolluted = Color(0xFFFF7300)
    val IAQHeavilyPolluted = Color(0xFFFF0000)
    val IAQSeverelyPolluted = Color(0xFF99004C)
    val IAQExtremelyPolluted = Color(0xFF663300)
    val IAQDangerouslyPolluted = Color(0xFF663300)
}

object GraphColors {
    val InfantryBlue = Color(red = 75, green = 119, blue = 190)
    val LightGreen = Color(0xFF4BF0BE)
    val Purple = Color(0xFF9C27B0)
    val Pink = Color(red = 255, green = 102, blue = 204)
    val Orange = Color(0xFFFF8800)
    val Gold = Color(0xFFFFD700)
    val Cyan = Color(0xFF00BCD4)
    val Red = Color(0xFFE91E63)
    val Blue = Color(0xFF2196F3)
    val Green = Color(0xFF4CAF50)
    val Teal = Color(0xFF009688)
    val Amber = Color(0xFFFFC107)
    val Lime = Color(0xFFCDDC39)
    val Indigo = Color(0xFF3F51B5)
    val DeepOrange = Color(0xFFFF5722)
    val Magenta = Color(0xFFE040FB)
    val SkyBlue = Color(0xFF03A9F4)
    val Chartreuse = Color(0xFF76FF03)
    val Coral = Color(0xFFFF6E40)
}

object StatusColors {
    val ColorScheme.StatusGreen: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFF10B981)
            } else {
                Color(0xFF059669)
            }

    val ColorScheme.StatusYellow: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFFF59E0B)
            } else {
                Color(0xFFD97706)
            }

    val ColorScheme.StatusOrange: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFFFB923C)
            } else {
                Color(0xFFF97316)
            }

    val ColorScheme.StatusRed: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFFEF4444)
            } else {
                Color(0xFFDC2626)
            }

    val ColorScheme.StatusBlue: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFF818CF8)
            } else {
                Color(0xFF4F46E5)
            }
}

object MessageItemColors {
    val Red = Color(0x4DFF0000)
}
