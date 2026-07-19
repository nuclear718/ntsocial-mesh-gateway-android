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
package com.ntsocial.meshlink.feature.connections.domain.usecase

import com.ntsocial.meshlink.core.network.SerialTransport
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import com.ntsocial.meshlink.feature.connections.model.JvmUsbDeviceData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import kotlin.coroutines.coroutineContext

@Single
class JvmUsbScanner : UsbScanner {
    override fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>> = flow {
        while (coroutineContext.isActive) {
            val ports =
                SerialTransport.getAvailablePorts().map { portName ->
                    DeviceListEntry.Usb(
                        usbData = JvmUsbDeviceData(portName),
                        name = portName,
                        fullAddress = "s$portName",
                        bonded = true, // Desktop serial ports don't need Android USB permission bonding
                        node = null,
                    )
                }
            emit(ports)
            delay(POLL_INTERVAL_MS)
        }
    }
        .distinctUntilChanged()

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
    }
}
