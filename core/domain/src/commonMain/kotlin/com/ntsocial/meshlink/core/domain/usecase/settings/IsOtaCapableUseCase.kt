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
package com.ntsocial.meshlink.core.domain.usecase.settings

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.DeviceHardwareRepository
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.isBle
import com.ntsocial.meshlink.core.repository.isSerial
import com.ntsocial.meshlink.core.repository.isTcp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single
import org.meshtastic.proto.HardwareModel

/** Use case to determine if the currently connected device is capable of over-the-air (OTA) updates. */
interface IsOtaCapableUseCase {
    operator fun invoke(): Flow<Boolean>
}

@Single
class IsOtaCapableUseCaseImpl(
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
    private val radioPrefs: RadioPrefs,
    private val deviceHardwareRepository: DeviceHardwareRepository,
) : IsOtaCapableUseCase {
    override operator fun invoke(): Flow<Boolean> =
        combine(nodeRepository.ourNodeInfo, radioController.connectionState) { node, connectionState ->
            node to connectionState
        }
            .flatMapLatest { (node, connectionState) ->
                if (node == null || connectionState != ConnectionState.Connected) {
                    flowOf(false)
                } else if (radioPrefs.isBle() || radioPrefs.isSerial() || radioPrefs.isTcp()) {
                    flow {
                        val hwModel = node.user.hw_model
                        val hw = deviceHardwareRepository.getDeviceHardwareByModel(hwModel.value).getOrNull()
                        // If we have hardware info, check if it's an architecture known to support OTA/DFU
                        val isOtaCapable =
                            hw?.let {
                                it.isEsp32Arc ||
                                    it.architecture.contains("nrf", ignoreCase = true) ||
                                    it.requiresDfu == true
                            } ?: (hwModel != HardwareModel.UNSET)
                        emit(isOtaCapable)
                    }
                } else {
                    flowOf(false)
                }
            }
}
