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
import com.ntsocial.meshlink.core.repository.NotificationPrefs
import com.ntsocial.meshlink.desktop.DesktopNotificationManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopNotificationManagerTest {

    /** Fake [NativeNotificationSender] that records dispatched notifications and allows controlling success/failure. */
    private class FakeNativeSender(var shouldSucceed: Boolean = true) : NativeNotificationSender {
        val sent = mutableListOf<Notification>()

        override fun send(notification: Notification): Boolean {
            sent.add(notification)
            return shouldSucceed
        }
    }

    /** Simple [NotificationPrefs] with all categories enabled by default. */
    private class FakeNotificationPrefs(
        messages: Boolean = true,
        nodeEvents: Boolean = true,
        lowBattery: Boolean = true,
    ) : NotificationPrefs {
        override val messagesEnabled = MutableStateFlow(messages)
        override val nodeEventsEnabled = MutableStateFlow(nodeEvents)
        override val nodeEventsAutoDisabledForEvent = MutableStateFlow(false)
        override val lowBatteryEnabled = MutableStateFlow(lowBattery)

        override fun setMessagesEnabled(enabled: Boolean) {
            messagesEnabled.value = enabled
        }

        override fun setNodeEventsEnabled(enabled: Boolean) {
            nodeEventsEnabled.value = enabled
        }

        override fun setNodeEventsAutoDisabledForEvent(disabled: Boolean) {
            nodeEventsAutoDisabledForEvent.value = disabled
        }

        override fun setLowBatteryEnabled(enabled: Boolean) {
            lowBatteryEnabled.value = enabled
        }
    }

    @Test
    fun `dispatch sends to native sender when enabled`() {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)

        manager.dispatch(Notification(title = "Test", message = "Hello"))

        Thread.sleep(ASYNC_WAIT_MS)
        assertEquals(1, sender.sent.size)
        assertEquals("Test", sender.sent[0].title)
    }

    @Test
    fun `dispatch respects disabled message preference`() {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(messages = false), sender)

        manager.dispatch(Notification(title = "Msg", message = "Hi", category = Notification.Category.Message))

        Thread.sleep(ASYNC_WAIT_MS)
        assertEquals(0, sender.sent.size, "Message notification should have been suppressed")
    }

    @Test
    fun `alerts are always dispatched even when messages disabled`() {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(messages = false), sender)

        manager.dispatch(Notification(title = "Alert", message = "Important", category = Notification.Category.Alert))

        Thread.sleep(ASYNC_WAIT_MS)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `fallback emitted when native sender fails`() = runBlocking {
        val sender = FakeNativeSender(shouldSucceed = false)
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)

        val fallback =
            async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(FALLBACK_WAIT_MS) { manager.fallbackNotifications.first() }
            }
        manager.dispatch(Notification(title = "Fallback", message = "Test"))

        val notification = assertNotNull(fallback.await(), "Expected fallback notification to be emitted")
        assertEquals("Fallback", notification.title)
    }

    @Test
    fun `no fallback when native sender succeeds`() = runBlocking {
        val sender = FakeNativeSender(shouldSucceed = true)
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)

        val fallback =
            async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(FALLBACK_WAIT_MS) { manager.fallbackNotifications.first() }
            }
        manager.dispatch(Notification(title = "Success", message = "Test"))

        assertNull(fallback.await(), "Should not emit fallback when native sender succeeds")
    }

    companion object {
        private const val ASYNC_WAIT_MS = 300L
        private const val FALLBACK_WAIT_MS = 1_000L
    }
}
