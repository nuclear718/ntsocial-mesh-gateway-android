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
package com.ntsocial.meshlink.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import com.ntsocial.meshlink.core.common.util.CommonUri
import org.jetbrains.compose.resources.StringResource
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@OptIn(ExperimentalComposeUiApi::class)
actual fun createClipEntry(text: String, label: String): ClipEntry = ClipEntry.withPlainText(text)

actual fun annotatedStringFromHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString = AnnotatedString(html)

@Composable actual fun rememberOpenNfcSettings(): () -> Unit = {}

@Composable actual fun rememberShowToast(): suspend (String) -> Unit = { _ -> }

@Composable actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = { _ -> }

@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit = { value ->
    runCatching {
        UIApplication.sharedApplication.openURL(
            url = NSURL(string = value),
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }
}

@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit = { _, _ -> }

@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit = { _ -> }

@Composable actual fun rememberReadTextFromUri(): suspend (uri: CommonUri, maxChars: Int) -> String? = { _, _ -> null }

@Composable actual fun KeepScreenOn(enabled: Boolean) {}

@Composable actual fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {}

@Composable actual fun rememberOpenLocationSettings(): () -> Unit = {}

@Composable
actual fun rememberRequestBluetoothPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {
    // CoreBluetooth owns the system prompt. Continuing starts the scan that triggers it; the repository still
    // rejects denied/disabled states and remains the source of truth.
    onGranted()
}

@Composable
actual fun rememberRequestLocalNetworkPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {}

@Composable actual fun isLocalNetworkPermissionGranted(): Boolean = true

@Composable
actual fun rememberRequestNotificationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {}

@Composable actual fun isLocationPermissionGranted(): Boolean = true

@Composable actual fun isGpsDisabled(): Boolean = false

@Composable actual fun SetScreenBrightness(brightness: Float) {}
