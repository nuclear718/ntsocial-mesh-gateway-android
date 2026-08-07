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
package com.ntsocial.meshlink.ios.runtime

import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayContract
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayWakeSink
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFNotificationCenterGetDarwinNotifyCenter
import platform.CoreFoundation.CFNotificationCenterPostNotification
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingUTF8

/** Payload-free Darwin notification; every receiver must re-read authenticated App Group state. */
internal object IosAppleGatewayWakeSink : AppleGatewayWakeSink {
    @OptIn(ExperimentalForeignApi::class)
    override fun stateChanged() {
        val name =
            requireNotNull(
                CFStringCreateWithCString(
                    kCFAllocatorDefault,
                    AppleGatewayContract.STATE_CHANGED_NOTIFICATION,
                    kCFStringEncodingUTF8,
                ),
            )
        try {
            CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), name, null, null, true)
        } finally {
            CFRelease(name)
        }
    }
}
