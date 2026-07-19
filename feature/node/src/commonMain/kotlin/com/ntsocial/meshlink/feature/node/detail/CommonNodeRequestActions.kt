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
package com.ntsocial.meshlink.feature.node.detail

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.ioDispatcher
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.TelemetryType
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.UiText
import com.ntsocial.meshlink.core.resources.neighbor_info
import com.ntsocial.meshlink.core.resources.position
import com.ntsocial.meshlink.core.resources.request_air_quality_metrics
import com.ntsocial.meshlink.core.resources.request_device_metrics
import com.ntsocial.meshlink.core.resources.request_environment_metrics
import com.ntsocial.meshlink.core.resources.request_host_metrics
import com.ntsocial.meshlink.core.resources.request_pax_metrics
import com.ntsocial.meshlink.core.resources.request_power_metrics
import com.ntsocial.meshlink.core.resources.requesting_from
import com.ntsocial.meshlink.core.resources.signal_quality
import com.ntsocial.meshlink.core.resources.traceroute
import com.ntsocial.meshlink.core.resources.user_info
import com.ntsocial.meshlink.core.ui.util.SnackbarManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(binds = [NodeRequestActions::class])
class CommonNodeRequestActions
constructor(
    private val radioController: RadioController,
    private val snackbarManager: SnackbarManager,
) : NodeRequestActions {

    private val _lastTracerouteTime = MutableStateFlow<Long?>(null)
    override val lastTracerouteTime: StateFlow<Long?> = _lastTracerouteTime.asStateFlow()

    private val _lastRequestNeighborTimes = MutableStateFlow<Map<Int, Long>>(emptyMap())
    override val lastRequestNeighborTimes: StateFlow<Map<Int, Long>> = _lastRequestNeighborTimes.asStateFlow()

    private suspend fun showFeedback(text: UiText) {
        snackbarManager.showSnackbar(message = text.resolve())
    }

    override fun requestUserInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(ioDispatcher) {
            Logger.i { "Requesting UserInfo for '$destNum'" }
            radioController.requestUserInfo(destNum)
            showFeedback(UiText.Resource(Res.string.requesting_from, Res.string.user_info, longName))
        }
    }

    override fun requestNeighborInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(ioDispatcher) {
            Logger.i { "Requesting NeighborInfo for '$destNum'" }
            val packetId = radioController.getPacketId()
            radioController.requestNeighborInfo(packetId, destNum)
            _lastRequestNeighborTimes.update { it + (destNum to nowMillis) }
            showFeedback(UiText.Resource(Res.string.requesting_from, Res.string.neighbor_info, longName))
        }
    }

    override fun requestPosition(scope: CoroutineScope, destNum: Int, longName: String, position: Position) {
        scope.launch(ioDispatcher) {
            Logger.i { "Requesting position for '$destNum'" }
            radioController.requestPosition(destNum, position)
            showFeedback(UiText.Resource(Res.string.requesting_from, Res.string.position, longName))
        }
    }

    override fun requestTelemetry(scope: CoroutineScope, destNum: Int, longName: String, type: TelemetryType) {
        scope.launch(ioDispatcher) {
            Logger.i { "Requesting telemetry for '$destNum'" }
            val packetId = radioController.getPacketId()
            radioController.requestTelemetry(packetId, destNum, type.ordinal)

            val typeRes =
                when (type) {
                    TelemetryType.DEVICE -> Res.string.request_device_metrics
                    TelemetryType.ENVIRONMENT -> Res.string.request_environment_metrics
                    TelemetryType.AIR_QUALITY -> Res.string.request_air_quality_metrics
                    TelemetryType.POWER -> Res.string.request_power_metrics
                    TelemetryType.LOCAL_STATS -> Res.string.signal_quality
                    TelemetryType.HOST -> Res.string.request_host_metrics
                    TelemetryType.PAX -> Res.string.request_pax_metrics
                }

            showFeedback(UiText.Resource(Res.string.requesting_from, typeRes, longName))
        }
    }

    override fun requestTraceroute(scope: CoroutineScope, destNum: Int, longName: String) {
        scope.launch(ioDispatcher) {
            Logger.i { "Requesting traceroute for '$destNum'" }
            val packetId = radioController.getPacketId()
            radioController.requestTraceroute(packetId, destNum)
            _lastTracerouteTime.value = nowMillis
            showFeedback(UiText.Resource(Res.string.requesting_from, Res.string.traceroute, longName))
        }
    }
}
