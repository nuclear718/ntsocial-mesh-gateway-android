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

import com.ntsocial.meshlink.core.ble.BluetoothState
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayContract
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.repository.RadioSessionState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.meshtastic.proto.ChannelSet

internal data class IosGatewayProjectionSignal(
    val bluetooth: BluetoothState,
    val connection: ConnectionState,
    val session: RadioSessionState,
    val databaseDeviceAddress: String?,
    val channels: ChannelSet,
    val channelSnapshotGeneration: Long,
    val inboundSessionRevision: Long,
)

internal fun iosGatewayProjectionSignals(
    bluetooth: Flow<BluetoothState>,
    connection: Flow<ConnectionState>,
    session: Flow<RadioSessionState>,
    databaseDeviceAddress: Flow<String?>,
    channels: Flow<ChannelSet>,
    channelSnapshotGeneration: Flow<Long>,
    inboundSessionRevision: Flow<Long>,
): Flow<IosGatewayProjectionSignal> {
    val channelProjection =
        combine(channels, channelSnapshotGeneration) { channelSet, generation -> channelSet to generation }
    val radioProjection =
        combine(bluetooth, connection, session, databaseDeviceAddress, channelProjection) {
                bluetoothState,
                connectionState,
                radioSession,
                databaseAddress,
                (channelSet, snapshotGeneration),
            ->
            IosGatewayProjectionSignal(
                bluetooth = bluetoothState,
                connection = connectionState,
                session = radioSession,
                databaseDeviceAddress = databaseAddress,
                channels = channelSet,
                channelSnapshotGeneration = snapshotGeneration,
                inboundSessionRevision = 0,
            )
        }
    return combine(radioProjection, inboundSessionRevision) { signal, revision ->
        signal.copy(inboundSessionRevision = revision)
    }
        .distinctUntilChanged()
}

/** Emits only delayed foreground ticks; foreground entry itself is handled by the explicit command/projection wake. */
internal fun iosGatewayRouteRefreshSignals(
    hostActive: Flow<Boolean>,
    refreshIntervalMillis: Long = IOS_GATEWAY_ROUTE_REFRESH_INTERVAL_MILLIS,
): Flow<Unit> {
    require(refreshIntervalMillis in 1 until AppleGatewayContract.ROUTE_TTL_MILLIS)
    return hostActive.distinctUntilChanged().flatMapLatest { active ->
        if (!active) {
            emptyFlow()
        } else {
            flow {
                while (currentCoroutineContext().isActive) {
                    delay(refreshIntervalMillis)
                    emit(Unit)
                }
            }
        }
    }
}

internal const val IOS_GATEWAY_ROUTE_REFRESH_INTERVAL_MILLIS = AppleGatewayContract.ROUTE_TTL_MILLIS / 2
