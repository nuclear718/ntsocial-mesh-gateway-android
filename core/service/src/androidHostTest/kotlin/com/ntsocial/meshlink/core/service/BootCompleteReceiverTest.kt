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
package com.ntsocial.meshlink.core.service

import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompleteReceiverTest {
    @Test
    fun `only completed boot and own package replacement restart the service`() {
        assertTrue(shouldRestartMeshService(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(shouldRestartMeshService(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(shouldRestartMeshService(Intent.ACTION_PACKAGE_REPLACED))
        assertFalse(shouldRestartMeshService("android.intent.action.QUICKBOOT_POWERON"))
        assertFalse(shouldRestartMeshService("com.ntsocial.meshlink.SIM_BOOT"))
        assertFalse(shouldRestartMeshService(null))
    }
}
