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
package com.ntsocial.meshlink.core.model.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/** Returns cryptographically secure random bytes supplied by Apple's Security framework. */
@OptIn(ExperimentalForeignApi::class)
actual fun platformRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "Random byte count must not be negative" }
    if (size == 0) return ByteArray(0)

    val bytes = ByteArray(size)
    val status =
        bytes.usePinned { pinned -> SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0)) }
    check(status == errSecSuccess) { "SecRandomCopyBytes failed with OSStatus $status" }
    return bytes
}
