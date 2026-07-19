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

import com.ntsocial.meshlink.core.repository.NotificationPrefs
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetNotificationSettingsUseCaseTest {

    private val notificationPrefs: NotificationPrefs = mock()
    private lateinit var useCase: SetNotificationSettingsUseCase

    @BeforeTest
    fun setUp() {
        useCase = SetNotificationSettingsUseCase(notificationPrefs)
    }

    @Test
    fun `setMessagesEnabled calls notificationPrefs`() {
        every { notificationPrefs.setMessagesEnabled(any()) } returns Unit
        useCase.setMessagesEnabled(true)
        verify { notificationPrefs.setMessagesEnabled(true) }
    }

    @Test
    fun `setNodeEventsEnabled calls notificationPrefs`() {
        every { notificationPrefs.setNodeEventsEnabled(any()) } returns Unit
        useCase.setNodeEventsEnabled(false)
        verify { notificationPrefs.setNodeEventsEnabled(false) }
    }

    @Test
    fun `setLowBatteryEnabled calls notificationPrefs`() {
        every { notificationPrefs.setLowBatteryEnabled(any()) } returns Unit
        useCase.setLowBatteryEnabled(true)
        verify { notificationPrefs.setLowBatteryEnabled(true) }
    }
}
