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
@file:Suppress("DEPRECATION")

package com.ntsocial.meshlink.core.ble

import com.juul.kable.Bluetooth
import com.juul.kable.Reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

/** iOS Bluetooth state and pairing semantics backed by Kable/CoreBluetooth. */
@Single(binds = [BluetoothRepository::class])
class IosBluetoothRepository : BluetoothRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BluetoothState())
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    @Volatile private var stateJob: Job? = null

    init {
        refreshState()
    }

    override fun refreshState() {
        initializePlatformBle()
        if (stateJob?.isActive == true) return
        stateJob =
            scope.launch {
                Bluetooth.availability.collect { availability ->
                    _state.value =
                        when (availability) {
                            Bluetooth.Availability.Available -> BluetoothState(hasPermissions = true, enabled = true)

                            is Bluetooth.Availability.Unavailable ->
                                BluetoothState(
                                    hasPermissions =
                                    when (availability.reason) {
                                        Reason.Unauthorized -> false
                                        Reason.Unknown -> _state.value.hasPermissions
                                        else -> true
                                    },
                                    enabled = false,
                                )
                        }
                }
            }
    }

    override fun isValid(bleAddress: String): Boolean = runCatching { Uuid.parse(bleAddress) }.isSuccess

    // CoreBluetooth does not expose a public bond database; protected characteristics trigger OS-managed pairing.
    override fun isBonded(address: String): Boolean = isValid(address)

    override suspend fun bond(device: BleDevice) {
        require(isValid(device.address)) { "Invalid CoreBluetooth peripheral identifier: ${device.address}" }
    }
}
