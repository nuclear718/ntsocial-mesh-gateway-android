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
package com.ntsocial.meshlink.desktop.radio

import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentityKeyProvider
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences

internal class DesktopNtsocialGatewayIdentityKeyProvider : NtsocialGatewayIdentityKeyProvider {
    override val legacyChannelHmacKey: ByteString =
        Preferences.userNodeForPackage(DesktopNtsocialGatewayIdentityKeyProvider::class.java)
            .let { preferences ->
                preferences
                    .get(KEY_NAME, null)
                    ?.let { encoded -> runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() }
                    ?.takeIf { it.size == KEY_SIZE_BYTES }
                    ?: ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes).also { generated ->
                        preferences.put(KEY_NAME, Base64.getEncoder().encodeToString(generated))
                        preferences.flush()
                    }
            }
            .toByteString()

    private companion object {
        const val KEY_NAME = "ntsocial_gateway_legacy_channel_hmac"
        const val KEY_SIZE_BYTES = 32
    }
}
