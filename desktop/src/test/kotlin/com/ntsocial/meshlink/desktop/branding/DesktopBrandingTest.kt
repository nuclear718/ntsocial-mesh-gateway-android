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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopBrandingTest {

    @Test
    fun `Windows selects NTsocial MeshLink identity`() {
        val branding = desktopBrandingFor(DesktopOS.Windows)

        assertEquals("NTsocial MeshLink", branding.productName)
        assertEquals("com.ntsocial.meshlink.desktop", branding.applicationId)
        assertEquals("NTsocial MeshLink", branding.notificationAppId)
        assertTrue(branding.isNtsocialWindows)
    }

    @Test
    fun `non-Windows hosts preserve Meshtastic Desktop identity`() {
        listOf(DesktopOS.MacOS, DesktopOS.Linux).forEach { os ->
            val branding = desktopBrandingFor(os)
            assertEquals("Meshtastic Desktop", branding.productName)
            assertEquals("Meshtastic", branding.notificationAppId)
            assertFalse(branding.isNtsocialWindows)
        }
    }

    @Test
    fun `splash phases follow exact three-second boundaries`() {
        assertSplashState(elapsedMillis = 0, backgroundAlpha = 0F, brandAlpha = 0F, overlayAlpha = 1F)
        assertSplashState(elapsedMillis = 500, backgroundAlpha = 0.5F, brandAlpha = 0F, overlayAlpha = 1F)
        assertSplashState(elapsedMillis = 1_000, backgroundAlpha = 1F, brandAlpha = 0F, overlayAlpha = 1F)
        assertSplashState(elapsedMillis = 1_500, backgroundAlpha = 1F, brandAlpha = 0.5F, overlayAlpha = 1F)
        assertSplashState(elapsedMillis = 2_000, backgroundAlpha = 1F, brandAlpha = 1F, overlayAlpha = 1F)
        assertSplashState(elapsedMillis = 2_500, backgroundAlpha = 1F, brandAlpha = 1F, overlayAlpha = 0.5F)

        val completed = windowsSplashVisualState(3_000)
        assertEquals(0F, completed.overlayAlpha)
        assertTrue(completed.isFinished)
    }

    @Test
    fun `cold launch splash is consumed once and never plays on other hosts`() {
        val playback = ColdLaunchSplashPlayback()

        assertFalse(playback.shouldPlay(DesktopOS.Linux))
        assertTrue(playback.shouldPlay(DesktopOS.Windows))
        assertFalse(playback.shouldPlay(DesktopOS.Windows))
    }

    @Test
    fun `Windows brand resources are available on the runtime classpath`() {
        val branding = desktopBrandingFor(DesktopOS.Windows)
        val resources =
            listOfNotNull(
                branding.windowIconResource,
                branding.trayIconResource,
                branding.appBarIconResource,
                "icon.ico",
            )

        resources.forEach { resource ->
            assertNotNull(javaClass.classLoader.getResource(resource), "Missing classpath resource: $resource")
        }
    }

    private fun assertSplashState(elapsedMillis: Long, backgroundAlpha: Float, brandAlpha: Float, overlayAlpha: Float) {
        val state = windowsSplashVisualState(elapsedMillis)
        assertEquals(backgroundAlpha, state.backgroundAlpha)
        assertEquals(brandAlpha, state.brandAlpha)
        assertEquals(overlayAlpha, state.overlayAlpha)
        assertFalse(state.isFinished)
    }
}
