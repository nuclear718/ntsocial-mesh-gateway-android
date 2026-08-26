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

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayHistoryState
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.ChannelSet
import java.util.UUID

/**
 * Publishes sanitized Gateway metadata after Koin has started.
 *
 * Events are explicit package-targeted notifications with no message bytes. Clients must re-query the protected
 * Provider for status, envelopes, nodes, or channels. `notifyChange` is emitted as an optional in-process observer
 * optimisation; it is not relied on for cross-app delivery on old Android releases.
 */
@Single
class NtsocialGatewayEventPublisher
internal constructor(
    private val context: Context,
    private val callerVerifier: NtsocialGatewayCallerVerifier,
    private val gatewayRepository: NtsocialGatewayRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val fleetRegistry: NtsocialEndpointGatewaySourceRegistry,
    @Named("ServiceScope") private val scope: CoroutineScope,
) {
    private val catalogGenerationTracker = GatewayCatalogGenerationTracker()
    private val initialCatalogSnapshot =
        GatewayCatalogSnapshot(ChannelSet(), catalogGenerationTracker.currentGeneration)
    private val catalogSnapshotState = MutableStateFlow(initialCatalogSnapshot)
    private val _channelSet = MutableStateFlow(initialCatalogSnapshot.channelSet)
    private val _radioGeneration = MutableStateFlow(initialCatalogSnapshot.radioGeneration)
    private val _historyState = MutableStateFlow(NtsocialGatewayHistoryState(HISTORY_NOT_READY, 0L))
    private val deliveredEnvelopeKeys = mutableSetOf<String>()

    private var started = false

    val channelSet: StateFlow<ChannelSet> = _channelSet.asStateFlow()
    val radioGeneration: StateFlow<String> = _radioGeneration.asStateFlow()
    internal val catalogSnapshot: StateFlow<GatewayCatalogSnapshot> = catalogSnapshotState.asStateFlow()
    val historyState: StateFlow<NtsocialGatewayHistoryState> = _historyState.asStateFlow()

    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }

        gatewayRepository.cachedEnvelopes.onEach(::onCachedEnvelopes).launchIn(scope)
        gatewayRepository.defaultChannelStatus.onEach { publishDataChanged(statusUri) }.launchIn(scope)
        serviceRepository.connectionState
            .onEach {
                publishDataChanged(statusUri)
                publishDataChanged(v2StatusUri)
            }
            .launchIn(scope)
        nodeRepository.nodeDBbyNum
            .onEach {
                publishDataChanged(nodesUri)
                publishDataChanged(statusUri)
            }
            .launchIn(scope)
        nodeRepository.myNodeInfo.onEach { publishDataChanged(statusUri) }.launchIn(scope)
        nodeRepository.ourNodeInfo.onEach { publishDataChanged(statusUri) }.launchIn(scope)
        radioConfigRepository.channelSetFlow
            .onEach { channelSet ->
                val radioGeneration = catalogGenerationTracker.update(channelSet)
                _channelSet.value = channelSet
                _radioGeneration.value = radioGeneration
                catalogSnapshotState.value = GatewayCatalogSnapshot(channelSet, radioGeneration)
                publishDataChanged(channelsUri)
                publishDataChanged(statusUri)
                publishDataChanged(v2ChannelsUri, NtsocialGatewayContract.EVENT_CHANNEL_CATALOG_CHANGED)
                publishDataChanged(v2StatusUri)
            }
            .launchIn(scope)
        packetRepository
            .getGatewayHistoryState(emptyList())
            .onEach { historyState ->
                _historyState.value = historyState
                publishDataChanged(v2MessageChangesUri, NtsocialGatewayContract.EVENT_MESSAGE_CHANGES_AVAILABLE)
                publishDataChanged(v2StatusUri)
            }
            .launchIn(scope)
        fleetRegistry.revision
            .onEach {
                publishDataChanged(v3StatusUri)
                publishDataChanged(v3EndpointsUri)
                publishDataChanged(v3ChannelsUri, NtsocialGatewayContract.EVENT_CHANNEL_CATALOG_CHANGED)
                publishDataChanged(v3MessageChangesUri, NtsocialGatewayContract.EVENT_MESSAGE_CHANGES_AVAILABLE)
            }
            .launchIn(scope)
    }

    fun publishCommandAccepted(targetPackage: String, requestId: String, packetId: Int) {
        publishEvent(
            targetPackages = setOf(targetPackage),
            eventType = NtsocialGatewayContract.EVENT_COMMAND_ACCEPTED,
            requestId = requestId,
            packetId = packetId,
            uri = envelopesUri,
        )
    }

    fun publishCommandRejected(targetPackage: String, requestId: String, reason: String) {
        publishEvent(
            targetPackages = setOf(targetPackage),
            eventType = NtsocialGatewayContract.EVENT_COMMAND_REJECTED,
            requestId = requestId,
            reason = reason,
            uri = statusUri,
        )
    }

    private fun onCachedEnvelopes(envelopes: List<NtsocialCachedEnvelope>) {
        val newEnvelopeCount =
            synchronized(deliveredEnvelopeKeys) {
                deliveredEnvelopeKeys.retainAll(envelopes.mapTo(mutableSetOf()) { it.deliveryKey() })
                envelopes.count { envelope -> deliveredEnvelopeKeys.add(envelope.deliveryKey()) }
            }
        if (newEnvelopeCount > 0) {
            publishDataChanged(envelopesUri, NtsocialGatewayContract.EVENT_ENVELOPE_AVAILABLE)
        }
    }

    private fun publishDataChanged(uri: Uri, eventType: String = NtsocialGatewayContract.EVENT_STATUS_CHANGED) {
        context.contentResolver.notifyChange(uri, null)
        publishEvent(targetPackages = callerVerifier.installedTrustedClientPackages(), eventType = eventType, uri = uri)
    }

    private fun publishEvent(
        targetPackages: Set<String>,
        eventType: String,
        requestId: String? = null,
        packetId: Int? = null,
        reason: String? = null,
        uri: Uri,
    ) {
        targetPackages.forEach { packageName ->
            val event =
                Intent(NtsocialGatewayContract.ACTION_EVENT)
                    .setPackage(packageName)
                    .putExtra(NtsocialGatewayContract.EXTRA_EVENT_TYPE, eventType)
                    .putExtra(NtsocialGatewayContract.EXTRA_URI, uri.toString())
            requestId?.let { event.putExtra(NtsocialGatewayContract.EXTRA_REQUEST_ID, it) }
            packetId?.let { event.putExtra(NtsocialGatewayContract.EXTRA_PACKET_ID, it) }
            reason?.let { event.putExtra(NtsocialGatewayContract.EXTRA_REASON, it) }
            context.sendBroadcast(event)
        }
    }

    private fun NtsocialCachedEnvelope.deliveryKey(): String = "${direction.name}:$packetId:$cacheKey"

    private val authority: String
        get() = NtsocialGatewayContract.authorityFor(context.packageName)

    private val statusUri: Uri
        get() = NtsocialGatewayContract.statusUri(authority)

    private val envelopesUri: Uri
        get() = NtsocialGatewayContract.envelopesUri(authority)

    private val nodesUri: Uri
        get() = NtsocialGatewayContract.nodesUri(authority)

    private val channelsUri: Uri
        get() = NtsocialGatewayContract.channelsUri(authority)

    private val v2StatusUri: Uri
        get() = NtsocialGatewayContract.v2StatusUri(authority)

    private val v2ChannelsUri: Uri
        get() = NtsocialGatewayContract.v2ChannelsUri(authority)

    private val v2MessageChangesUri: Uri
        get() = NtsocialGatewayContract.v2MessageChangesUri(authority)

    private val v3StatusUri: Uri
        get() = NtsocialGatewayContract.V3.statusUri(authority)

    private val v3EndpointsUri: Uri
        get() = NtsocialGatewayContract.V3.endpointsUri(authority)

    private val v3ChannelsUri: Uri
        get() = NtsocialGatewayContract.V3.channelsUri(authority)

    private val v3MessageChangesUri: Uri
        get() = NtsocialGatewayContract.V3.endpointsUri(authority)

    private companion object {
        const val HISTORY_NOT_READY = "not-ready"
    }
}

/** One atomic Provider/command view of the configured channels and their opaque route generation. */
internal data class GatewayCatalogSnapshot(val channelSet: ChannelSet, val radioGeneration: String)

/** Opaque runtime generation: no exported value is derived from ChannelSet or its PSKs. */
internal class GatewayCatalogGenerationTracker(
    private val newGeneration: () -> String = { UUID.randomUUID().toString() },
) {
    private var lastChannelSet: ChannelSet? = null
    var currentGeneration: String = newGeneration()
        private set

    fun update(channelSet: ChannelSet): String {
        if (lastChannelSet != channelSet) {
            lastChannelSet = channelSet
            currentGeneration = newGeneration()
        }
        return currentGeneration
    }
}
