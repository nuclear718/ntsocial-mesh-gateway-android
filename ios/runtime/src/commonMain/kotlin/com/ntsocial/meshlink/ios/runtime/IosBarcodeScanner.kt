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

/** Swift host boundary for the system-provided, on-device iOS QR scanner. */
interface IosBarcodeScannerHost {
    val isSupported: Boolean

    fun startScan(requestId: Long)
}

/** Owns the single in-flight scanner callback without exposing Kotlin function types to Swift. */
internal class IosBarcodeScannerCoordinator {
    private var host: IosBarcodeScannerHost? = null
    private var nextRequestId = 0L
    private var pendingRequest: PendingRequest? = null

    val isSupported: Boolean
        get() = host?.isSupported == true

    fun install(host: IosBarcodeScannerHost) {
        if (this.host === host) return
        cancelPendingRequest()
        this.host = host
    }

    fun uninstall(host: IosBarcodeScannerHost) {
        if (this.host !== host) return
        this.host = null
        cancelPendingRequest()
    }

    fun startScan(onResult: (String?) -> Unit) {
        val installedHost = host ?: return
        if (!installedHost.isSupported || pendingRequest != null) return

        val requestId = ++nextRequestId
        pendingRequest = PendingRequest(requestId, onResult)
        installedHost.startScan(requestId)
    }

    fun complete(requestId: Long, contents: String?) {
        val request = pendingRequest?.takeIf { it.id == requestId } ?: return
        pendingRequest = null
        request.onResult(contents)
    }

    private fun cancelPendingRequest() {
        val callback = pendingRequest?.onResult ?: return
        pendingRequest = null
        callback(null)
    }

    private data class PendingRequest(val id: Long, val onResult: (String?) -> Unit)
}
