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
package com.ntsocial.meshlink.core.ble

import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

@Single(binds = [BleScanner::class])
class KableBleScanner(private val loggingConfig: BleLoggingConfig) : BleScanner {
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        initializePlatformBle()
        val filterPlan = kableScanFilterPlan(serviceUuid, address, platformSupportsBleScanAddressFilter)
        val scanner = Scanner {
            logging { applyConfig(loggingConfig) }
            // When both address and serviceUuid are provided, use OR-semantics so the device
            // is found even if one filter is ineffective on a platform that supports both.
            // CoreBluetooth rejects Filter.Address entirely, so Apple keeps only the native
            // service filter; BleRadioTransport retains the exact identifier comparison.
            if (filterPlan.includeAddress && filterPlan.includeService) {
                filters {
                    match { this.address = requireNotNull(address) }
                    match { services = listOf(requireNotNull(serviceUuid)) }
                }
            } else if (filterPlan.includeAddress) {
                filters { match { this.address = requireNotNull(address) } }
            } else if (filterPlan.includeService) {
                filters { match { services = listOf(requireNotNull(serviceUuid)) } }
            }
        }

        // Kable's Scanner doesn't enforce timeout internally, it runs until the Flow is cancelled.
        // By wrapping it in a channelFlow with a timeout, we enforce the BleScanner contract cleanly.
        return channelFlow {
            withTimeoutOrNull(timeout) {
                scanner.advertisements.collect { advertisement ->
                    send(
                        MeshtasticBleDevice(
                            address = advertisement.identifier.toString(),
                            name = advertisement.name,
                            advertisement = advertisement,
                        ),
                    )
                }
            }
        }
    }
}

internal data class KableScanFilterPlan(val includeService: Boolean, val includeAddress: Boolean)

internal fun kableScanFilterPlan(
    serviceUuid: Uuid?,
    address: String?,
    supportsAddressFilter: Boolean,
): KableScanFilterPlan =
    KableScanFilterPlan(includeService = serviceUuid != null, includeAddress = address != null && supportsAddressFilter)
