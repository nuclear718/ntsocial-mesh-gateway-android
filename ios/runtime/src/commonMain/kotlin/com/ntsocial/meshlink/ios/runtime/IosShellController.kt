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

import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayContract
import com.ntsocial.meshlink.core.navigation.DeepLinkRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

internal data class AppleHostReadiness(
    val bundleIdentifier: String?,
    val appGroupContainerAvailable: Boolean,
    val bluetoothPrivacyDescriptionConfigured: Boolean,
    val bluetoothCentralBackgroundModeConfigured: Boolean,
    val companionUrlSchemeConfigured: Boolean,
)

internal enum class ParentHandoffState {
    NONE,
    ACCEPTED,
    REJECTED,
}

internal data class IosShellState(
    val hostActive: Boolean = false,
    val radio: RadioUiState = RadioUiState(),
    val integrationChecks: Int = 0,
    val hostReadiness: AppleHostReadiness? = null,
    val parentHandoffState: ParentHandoffState = ParentHandoffState.NONE,
    val pendingNavigationUrl: String? = null,
)

/** Host-owned controller that joins real radio facts with Apple integration readiness. */
internal class IosShellController(private val radioUiPort: RadioUiPort, scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(IosShellState(radio = radioUiPort.state.value))
    val state: StateFlow<IosShellState> = mutableState.asStateFlow()
    private val radioObservation: Job =
        radioUiPort.state
            .onEach { radioState -> mutableState.update { current -> current.copy(radio = radioState) } }
            .launchIn(scope)

    fun setHostActive(active: Boolean) {
        mutableState.update { current -> current.copy(hostActive = active) }
    }

    fun refreshBluetooth() {
        radioUiPort.refreshBluetooth()
    }

    fun toggleRadioScan() {
        if (radioUiPort.state.value.scanning) radioUiPort.stopScan() else radioUiPort.startScan()
    }

    fun connectRadio(peripheralId: String) {
        radioUiPort.connect(peripheralId)
    }

    fun disconnectRadio() {
        radioUiPort.disconnect()
    }

    fun forgetRadio() {
        radioUiPort.forget()
    }

    fun refreshHostReadiness() {
        val readiness = inspectAppleHost()
        mutableState.update { current ->
            current.copy(integrationChecks = current.integrationChecks + 1, hostReadiness = readiness)
        }
    }

    fun handleOpenUrl(url: String) {
        val accepted = url == AppleGatewayContract.PROCESS_DEEP_LINK
        mutableState.update { current ->
            when {
                accepted -> current.copy(parentHandoffState = ParentHandoffState.ACCEPTED)
                isFeatureDeepLink(url) -> current.copy(pendingNavigationUrl = url)
                else -> current.copy(parentHandoffState = ParentHandoffState.REJECTED)
            }
        }
    }

    fun consumeNavigationUrl(url: String) {
        mutableState.update { current ->
            if (current.pendingNavigationUrl == url) current.copy(pendingNavigationUrl = null) else current
        }
    }

    fun close() {
        radioObservation.cancel()
        radioUiPort.close()
    }
}

private fun isFeatureDeepLink(url: String): Boolean =
    runCatching { DeepLinkRouter.route(CommonUri.parse(url)) != null }.getOrDefault(false)

internal expect fun inspectAppleHost(): AppleHostReadiness
