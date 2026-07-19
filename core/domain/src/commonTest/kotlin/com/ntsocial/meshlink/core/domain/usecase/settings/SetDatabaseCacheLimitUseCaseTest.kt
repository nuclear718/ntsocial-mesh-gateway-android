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

import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.database.DatabaseConstants
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetDatabaseCacheLimitUseCaseTest {

    private lateinit var databaseManager: DatabaseManager
    private lateinit var useCase: SetDatabaseCacheLimitUseCase

    @BeforeTest
    fun setUp() {
        databaseManager = mock(dev.mokkery.MockMode.autofill)
        useCase = SetDatabaseCacheLimitUseCase(databaseManager)
    }

    @Test
    fun `invoke calls setCacheLimit with clamped value`() {
        // Act & Assert
        useCase(0)
        verify { databaseManager.setCacheLimit(DatabaseConstants.MIN_CACHE_LIMIT) }

        useCase(100)
        verify { databaseManager.setCacheLimit(DatabaseConstants.MAX_CACHE_LIMIT) }

        useCase(5)
        verify { databaseManager.setCacheLimit(5) }
    }
}
