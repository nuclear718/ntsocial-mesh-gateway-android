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
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager

internal actual fun inspectAppleHost(): AppleHostReadiness {
    val bundle = NSBundle.mainBundle
    val backgroundModes = bundle.objectForInfoDictionaryKey("UIBackgroundModes") as? List<*>
    val urlTypes = bundle.objectForInfoDictionaryKey("CFBundleURLTypes") as? List<*>
    val configuredSchemes =
        urlTypes.orEmpty().flatMap { urlType ->
            val dictionary = urlType as? Map<*, *> ?: return@flatMap emptyList()
            (dictionary["CFBundleURLSchemes"] as? List<*>).orEmpty().filterIsInstance<String>()
        }

    return AppleHostReadiness(
        bundleIdentifier = bundle.bundleIdentifier,
        appGroupContainerAvailable =
        NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(
            AppleGatewayContract.APP_GROUP_IDENTIFIER,
        ) != null,
        bluetoothPrivacyDescriptionConfigured =
        bundle.objectForInfoDictionaryKey("NSBluetoothAlwaysUsageDescription") is String,
        bluetoothCentralBackgroundModeConfigured = "bluetooth-central" in backgroundModes.orEmpty(),
        companionUrlSchemeConfigured = AppleGatewayContract.COMPANION_URL_SCHEME in configuredSchemes,
    )
}
