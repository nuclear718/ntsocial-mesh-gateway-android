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

import androidx.compose.ui.window.ComposeUIViewController
import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.MainScope
import org.koin.compose.KoinIsolatedContext
import platform.UIKit.UIViewController

/** Swift-facing lifecycle and root-controller boundary for the iOS host. */
object MeshLinkRuntime {
    private val compositionRoot = IosCompositionRoot()
    private val controllerScope = MainScope()
    private val shellController: IosShellController by lazy {
        val koin = compositionRoot.koin
        IosShellController(
            radioUiPort =
            createRadioUiPort(
                scanner = koin.get<BleScanner>(),
                bluetoothRepository = koin.get<BluetoothRepository>(),
                radioController = koin.get<RadioController>(),
                radioInterfaceService = koin.get<RadioInterfaceService>(),
                serviceRepository = koin.get<ServiceRepository>(),
                channelOperationLock = koin.get<ChannelOperationLock>(),
            ),
            scope = controllerScope,
        )
    }

    fun configureGateway(sharedContainerPath: String, hmacKeyBase64: String) {
        compositionRoot.configureGateway(sharedContainerPath, hmacKeyBase64)
    }

    fun clearGatewayConfiguration() {
        compositionRoot.clearGatewayConfiguration()
    }

    fun processGatewayCommands() {
        compositionRoot.processGatewayCommands()
    }

    fun makeRootViewController(): UIViewController {
        compositionRoot.start()
        return ComposeUIViewController {
            KoinIsolatedContext(context = compositionRoot.application) { IosShellApp(shellController) }
        }
    }

    fun setHostActive(active: Boolean) {
        compositionRoot.setHostActive(active)
        shellController.setHostActive(active)
    }

    fun handleOpenUrl(url: String) {
        shellController.handleOpenUrl(url)
        if (url == com.ntsocial.meshlink.core.gateway.apple.AppleGatewayContract.PROCESS_DEEP_LINK) {
            compositionRoot.processGatewayCommands()
        }
    }
}
