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

import kotlinx.cinterop.ExperimentalForeignApi
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test

class IosCompositionRootTest {
    @Test
    @OptIn(ExperimentalForeignApi::class)
    fun gatewayAndRadioGraphResolveTogether() {
        val directory = "${NSTemporaryDirectory()}/meshlink-gateway-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        val root = IosCompositionRoot()
        try {
            val key = ByteArray(32) { index -> index.toByte() }.toByteString().base64()
            root.configureGateway(sharedContainerPath = directory, hmacKeyBase64 = key)
            root.start()
            root.processGatewayCommands()
            root.setHostActive(true)
        } finally {
            root.close()
            NSFileManager.defaultManager.removeItemAtPath(directory, null)
        }
    }
}
