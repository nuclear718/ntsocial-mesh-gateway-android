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
package com.ntsocial.meshlink.feature.meshcore

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class MeshCoreNtsocialVisualsTest {
    @Test
    fun typographyMatchesAndroidNtsocialReference() {
        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.bodyLarge.fontFamily)
        assertEquals(FontWeight.Normal, MeshCoreNtsocialTypography.bodyLarge.fontWeight)
        assertEquals(16.sp, MeshCoreNtsocialTypography.bodyLarge.fontSize)
        assertEquals(22.sp, MeshCoreNtsocialTypography.bodyLarge.lineHeight)

        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.bodyMedium.fontFamily)
        assertEquals(14.sp, MeshCoreNtsocialTypography.bodyMedium.fontSize)
        assertEquals(18.sp, MeshCoreNtsocialTypography.bodyMedium.lineHeight)

        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.bodySmall.fontFamily)
        assertEquals(12.sp, MeshCoreNtsocialTypography.bodySmall.fontSize)
        assertEquals(16.sp, MeshCoreNtsocialTypography.bodySmall.lineHeight)

        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.headlineSmall.fontFamily)
        assertEquals(FontWeight.Medium, MeshCoreNtsocialTypography.headlineSmall.fontWeight)
        assertEquals(18.sp, MeshCoreNtsocialTypography.headlineSmall.fontSize)
        assertEquals(24.sp, MeshCoreNtsocialTypography.headlineSmall.lineHeight)

        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.titleMedium.fontFamily)
        assertEquals(FontWeight.Medium, MeshCoreNtsocialTypography.titleMedium.fontWeight)
        assertEquals(16.sp, MeshCoreNtsocialTypography.titleMedium.fontSize)
        assertEquals(22.sp, MeshCoreNtsocialTypography.titleMedium.lineHeight)

        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.labelMedium.fontFamily)
        assertEquals(FontWeight.Medium, MeshCoreNtsocialTypography.labelMedium.fontWeight)
        assertEquals(13.sp, MeshCoreNtsocialTypography.labelMedium.fontSize)
        assertEquals(18.sp, MeshCoreNtsocialTypography.labelMedium.lineHeight)

        assertEquals(FontFamily.Monospace, MeshCoreNtsocialTypography.labelSmall.fontFamily)
        assertEquals(FontWeight.Normal, MeshCoreNtsocialTypography.labelSmall.fontWeight)
        assertEquals(11.sp, MeshCoreNtsocialTypography.labelSmall.fontSize)
        assertEquals(16.sp, MeshCoreNtsocialTypography.labelSmall.lineHeight)
    }
}
