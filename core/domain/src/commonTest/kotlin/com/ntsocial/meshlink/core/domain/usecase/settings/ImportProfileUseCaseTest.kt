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
package com.ntsocial.meshlink.core.domain.usecase.settings

import okio.Buffer
import org.meshtastic.proto.DeviceProfile
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportProfileUseCaseTest {

    private lateinit var useCase: ImportProfileUseCase

    @BeforeTest
    fun setUp() {
        useCase = ImportProfileUseCase()
    }

    @Test
    fun `invoke with valid data returns profile`() {
        // Arrange
        val profile = DeviceProfile(long_name = "Test Node")
        val buffer = Buffer().write(profile.encode())

        // Act
        val result = useCase(buffer)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals("Test Node", result.getOrNull()?.long_name)
    }

    @Test
    fun `invoke with invalid data returns failure`() {
        // Arrange
        val buffer = Buffer().write(byteArrayOf(1, 2, 3))

        // Act
        val result = useCase(buffer)

        // Assert
        assertTrue(result.isFailure)
    }
}
