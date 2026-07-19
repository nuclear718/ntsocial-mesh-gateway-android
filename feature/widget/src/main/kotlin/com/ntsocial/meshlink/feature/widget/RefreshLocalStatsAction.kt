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
package com.ntsocial.meshlink.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.model.TelemetryType
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.NodeManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RefreshLocalStatsAction :
    ActionCallback,
    KoinComponent {

    private val commandSender: CommandSender by inject()
    private val nodeManager: NodeManager by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val myNodeNum = nodeManager.myNodeNum.value
        if (myNodeNum == null) {
            Logger.w { "RefreshLocalStatsAction: myNodeNum is null, skipping telemetry request" }
            return
        }

        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.LOCAL_STATS.ordinal)
        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.DEVICE.ordinal)
    }
}
