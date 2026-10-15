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
package com.ntsocial.meshlink.feature.firmware.di

import com.ntsocial.meshlink.core.ui.viewmodel.EndpointViewModelFactory
import com.ntsocial.meshlink.feature.firmware.DefaultFirmwareUpdateManager
import com.ntsocial.meshlink.feature.firmware.FirmwareUpdateManager
import com.ntsocial.meshlink.feature.firmware.FirmwareUpdateViewModel
import com.ntsocial.meshlink.feature.firmware.UsbUpdateHandler
import com.ntsocial.meshlink.feature.firmware.ota.Esp32OtaUpdateHandler
import com.ntsocial.meshlink.feature.firmware.ota.dfu.SecureDfuHandler
import org.koin.core.module.dsl.scopedOf
import org.koin.dsl.ScopeDSL
import org.koin.dsl.bind

fun EndpointViewModelFactory.registerFirmwareEndpointViewModels() {
    register(FirmwareUpdateViewModel::class) { _, _ ->
        FirmwareUpdateViewModel(
            firmwareReleaseRepository = get(),
            deviceHardwareRepository = get(),
            nodeRepository = get(),
            radioController = get(),
            radioPrefs = get(),
            bootloaderWarningDataSource = get(),
            firmwareUpdateManager = get(),
            usbManager = get(),
            fileHandler = get(),
            applicationScope = get(),
        )
    }
}

/** Firmware handlers also carry a radio owner; root handlers must never be reused by a secondary. */
fun ScopeDSL.endpointFirmwareDependencies() {
    scopedOf(::SecureDfuHandler)
    scopedOf(::Esp32OtaUpdateHandler)
    scopedOf(::UsbUpdateHandler)
    scopedOf(::DefaultFirmwareUpdateManager).bind<FirmwareUpdateManager>()
}
