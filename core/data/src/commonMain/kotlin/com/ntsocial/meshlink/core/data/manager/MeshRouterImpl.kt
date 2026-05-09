/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.core.data.manager

import com.ntsocial.meshlink.core.repository.MeshActionHandler
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.MeshConfigHandler
import com.ntsocial.meshlink.core.repository.MeshDataHandler
import com.ntsocial.meshlink.core.repository.MeshRouter
import com.ntsocial.meshlink.core.repository.MqttManager
import com.ntsocial.meshlink.core.repository.NeighborInfoHandler
import com.ntsocial.meshlink.core.repository.TracerouteHandler
import com.ntsocial.meshlink.core.repository.XModemManager
import org.koin.core.annotation.Single

/** Implementation of [MeshRouter] that orchestrates specialized mesh packet handlers. */
@Suppress("LongParameterList")
@Single
class MeshRouterImpl(
    private val dataHandlerLazy: Lazy<MeshDataHandler>,
    private val configHandlerLazy: Lazy<MeshConfigHandler>,
    private val tracerouteHandlerLazy: Lazy<TracerouteHandler>,
    private val neighborInfoHandlerLazy: Lazy<NeighborInfoHandler>,
    private val configFlowManagerLazy: Lazy<MeshConfigFlowManager>,
    private val mqttManagerLazy: Lazy<MqttManager>,
    private val actionHandlerLazy: Lazy<MeshActionHandler>,
    private val xmodemManagerLazy: Lazy<XModemManager>,
) : MeshRouter {
    override val dataHandler: MeshDataHandler
        get() = dataHandlerLazy.value

    override val configHandler: MeshConfigHandler
        get() = configHandlerLazy.value

    override val tracerouteHandler: TracerouteHandler
        get() = tracerouteHandlerLazy.value

    override val neighborInfoHandler: NeighborInfoHandler
        get() = neighborInfoHandlerLazy.value

    override val configFlowManager: MeshConfigFlowManager
        get() = configFlowManagerLazy.value

    override val mqttManager: MqttManager
        get() = mqttManagerLazy.value

    override val actionHandler: MeshActionHandler
        get() = actionHandlerLazy.value

    override val xmodemManager: XModemManager
        get() = xmodemManagerLazy.value
}
