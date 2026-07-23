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
package com.ntsocial.meshlink.desktop.notification

import com.ntsocial.meshlink.core.repository.Notification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsNotificationSenderTest {

    private val sender = WindowsNotificationSender(appName = "TestApp")

    @Test
    fun `default notification app id uses NTsocial MeshLink product name`() {
        val cmd = WindowsNotificationSender().buildCommand(Notification(title = "Hi", message = "There"))
        val script = cmd[cmd.indexOf("-Command") + 1]

        assertTrue(script.contains("CreateToastNotifier('NTsocial MeshLink')"))
    }

    @Test
    fun `command starts with powershell`() {
        val notification = Notification(title = "Hi", message = "There")
        val cmd = sender.buildCommand(notification)
        assertEquals("powershell.exe", cmd[0])
        assertTrue(cmd.contains("-NoProfile"))
        assertTrue(cmd.contains("-NonInteractive"))
    }

    @Test
    fun `silent notification sets audio silent`() {
        val notification = Notification(title = "Quiet", message = "Shhh", isSilent = true)
        val cmd = sender.buildCommand(notification)
        val script = cmd[cmd.indexOf("-Command") + 1]
        assertTrue(script.contains("silent='true'"), "Expected silent audio in: $script")
    }

    @Test
    fun `non-silent notification uses default sound`() {
        val notification = Notification(title = "Loud", message = "Hey", isSilent = false)
        val cmd = sender.buildCommand(notification)
        val script = cmd[cmd.indexOf("-Command") + 1]
        assertTrue(script.contains("Notification.Default"), "Expected default sound in: $script")
    }

    @Test
    fun `title and message passed as positional args`() {
        val notification = Notification(title = "My Title", message = "My Message")
        val cmd = sender.buildCommand(notification)
        // Last two args should be the title and message
        assertEquals("My Message", cmd.last())
        assertEquals("My Title", cmd[cmd.size - 2])
    }

    @Test
    fun `user content is not interpolated into script template`() {
        val notification = Notification(title = "'; Drop-Database", message = "<script>alert(1)</script>")
        val cmd = sender.buildCommand(notification)
        val script = cmd[cmd.indexOf("-Command") + 1]
        // Script should use $args[0] and $args[1], not raw user content
        assertFalse(script.contains("Drop-Database"), "User title leaked into script: $script")
        assertFalse(script.contains("<script>"), "User message leaked into script: $script")
        // But the positional args should carry the raw content
        assertEquals("<script>alert(1)</script>", cmd.last())
        assertEquals("'; Drop-Database", cmd[cmd.size - 2])
    }

    @Test
    fun `script uses SecurityElement Escape for XML safety`() {
        val notification = Notification(title = "Test", message = "Test")
        val cmd = sender.buildCommand(notification)
        val script = cmd[cmd.indexOf("-Command") + 1]
        assertTrue(script.contains("[System.Security.SecurityElement]::Escape"), "Expected XML escaping in: $script")
    }

    @Test
    fun `appName with single quotes is escaped in script`() {
        val evilSender = WindowsNotificationSender(appName = "Mesh'Evil")
        val notification = Notification(title = "Test", message = "Test")
        val cmd = evilSender.buildCommand(notification)
        val script = cmd[cmd.indexOf("-Command") + 1]
        // Single quotes should be doubled in PowerShell single-quoted strings
        assertTrue(script.contains("Mesh''Evil"), "Expected doubled quote in: $script")
    }
}
