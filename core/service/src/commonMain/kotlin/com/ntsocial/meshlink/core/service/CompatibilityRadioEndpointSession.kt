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
package com.ntsocial.meshlink.core.service

import com.ntsocial.meshlink.core.database.DatabaseManager
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.radiofleet.EndpointSessionState
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSession
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.MeshMessageProcessor
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Host-owned compatibility dependencies remain alive for accepted Gateway replay after the BLE lease is released. */
data class CompatibilityRadioDependencies(
    val radio: RadioInterfaceService,
    val service: ServiceRepository,
    val prefs: RadioPrefs,
    val database: DatabaseManager,
    val nodes: NodeManager,
    val ingress: MeshMessageProcessor,
    val packets: PacketHandler,
    val gatewayGate: GatewayIngressSessionGate,
    val operationLock: ChannelOperationLock,
    val dispatchers: CoroutineDispatchers,
)

class CompatibilityRadioEndpointSession(
    private val profile: RadioEndpointProfile,
    private val dependencies: CompatibilityRadioDependencies,
) : RadioEndpointSession {
    override val endpointId = profile.id
    private val scope = CoroutineScope(SupervisorJob() + dependencies.dispatchers.default)
    private val mutableState = MutableStateFlow<EndpointSessionState>(EndpointSessionState.Registered)
    override val state = mutableState.asStateFlow()
    private val mutableGeneration = MutableStateFlow(dependencies.radio.radioSessionState.value.epoch)
    override val generation = mutableGeneration.asStateFlow()

    init {
        scope.launch {
            dependencies.radio.radioSessionState.collect { session ->
                mutableGeneration.value = session.epoch
                mutableState.value =
                    when {
                        session.isConfiguredReady -> EndpointSessionState.Ready(session.epoch)

                        session.transportConnectionState == ConnectionState.Connected ->
                            EndpointSessionState.Synchronizing

                        dependencies.service.connectionState.value == ConnectionState.Connecting ->
                            EndpointSessionState.Connecting

                        else -> EndpointSessionState.Registered
                    }
            }
        }
    }

    override suspend fun start() {
        if (dependencies.radio.radioSessionState.value.isConfiguredReady) return
        stop()
        dependencies.operationLock.withLock {
            dependencies.prefs.setDevAddrAndAwait(profile.transportAddress)
            dependencies.prefs.setDevName(profile.displayName)
            val address = dependencies.radio.awaitHydratedDeviceAddress()
            check(address == profile.transportAddress) { "Compatibility endpoint selection changed during startup" }
            dependencies.database.switchActiveDatabase(address)
            dependencies.nodes.loadCachedNodeDBAndAwait()
            dependencies.ingress.resumeIngress()
            mutableState.value = EndpointSessionState.Connecting
            dependencies.radio.connect()
        }
        val ready =
            withTimeoutOrNull(90.seconds) { dependencies.radio.radioSessionState.first { it.isConfiguredReady } }
        if (ready == null) mutableState.value = EndpointSessionState.Degraded("setup_timeout")
    }

    override fun revoke() {
        dependencies.gatewayGate.invalidate()
        dependencies.radio.revokeConnection()
    }

    override suspend fun stop() {
        revoke()
        dependencies.operationLock.withLock { dependencies.radio.disconnect() }
        // Never wait for a readback producer while holding its short admission lock.
        dependencies.ingress.quiesceIngress()
        dependencies.packets.stopPacketQueueAndAwait()
        mutableState.value = EndpointSessionState.Registered
    }

    override suspend fun close() {
        stop()
        scope.coroutineContext[Job]?.cancelAndJoin()
    }
}
