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
@file:Suppress("TooGenericExceptionCaught")

package com.ntsocial.meshlink.ios.runtime

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayCallerCredentials
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayContract
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayNativeMessageChange
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayOverlayIngressPayload
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayPrivateLedger
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayProviderEngine
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayReadiness
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRouteRegistry
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewaySchema
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayStore
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.decodeBase64

/**
 * Owns the iOS companion side of the authenticated App Group Gateway.
 *
 * App Group SQLite is a projection/mailbox only. Authoritative route capabilities stay in memory, restart-stable
 * idempotency stays in the companion's private Application Support directory, and radio admission continues to use the
 * existing Room + [IosDurableMessageQueue] path.
 */
internal class IosAppleGatewayCoordinator(
    configuration: AppleGatewayRuntimeConfiguration,
    bluetoothRepository: BluetoothRepository,
    serviceRepository: ServiceRepository,
    private val radioInterfaceService: RadioInterfaceService,
    radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val gatewayRepository: NtsocialGatewayRepository,
    private val databaseManager: DatabaseManager,
    private val channelOperationLock: ChannelOperationLock,
    dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val operationMutex = Mutex()
    private val processedOverlayKeys = LinkedHashSet<String>()
    private val store = AppleGatewayStore("${configuration.sharedContainerPath}/${AppleGatewaySchema.FILE_NAME}")
    private val engine =
        AppleGatewayProviderEngine(
            store = store,
            ledger = AppleGatewayPrivateLedger("${iosApplicationSupportDirectory()}/apple-gateway-private-v1.sqlite"),
            radioPort =
            IosAppleGatewayRadioPort(
                store = store,
                bluetoothRepository = bluetoothRepository,
                serviceRepository = serviceRepository,
                radioInterfaceService = radioInterfaceService,
                databaseManager = databaseManager,
                radioConfigRepository = radioConfigRepository,
                packetRepository = packetRepository,
                gatewayRepository = gatewayRepository,
                channelOperationLock = channelOperationLock,
            ),
            routeRegistry = AppleGatewayRouteRegistry(),
            credentials =
            AppleGatewayCallerCredentials(
                callerId = AppleGatewayContract.PARENT_CALLER_ID,
                activeKeyVersion = ACTIVE_KEY_VERSION,
                authenticationKey =
                requireNotNull(configuration.hmacKeyBase64.decodeBase64()) {
                    "Apple Gateway authentication key is not valid Base64"
                },
            ),
            wakeSink = IosAppleGatewayWakeSink,
        )
    private val projectionSignals =
        iosGatewayProjectionSignals(
            bluetooth = bluetoothRepository.state,
            connection = serviceRepository.connectionState,
            session = radioInterfaceService.radioSessionState,
            databaseDeviceAddress = databaseManager.currentAddress,
            channels = radioConfigRepository.channelSetFlow,
            channelSnapshotGeneration = radioConfigRepository.channelSnapshotGeneration,
            inboundSessionRevision = gatewayRepository.inboundSessionRevision,
        )
    private val retryScheduler =
        BoundedGatewayRetryScheduler(
            scope = scope,
            maxAttempts = MAX_PENDING_RETRY_ATTEMPTS,
            delayMillisForAttempt = ::pendingRetryDelayMillis,
            retry = ::retryPendingCommandsWhileReady,
        )

    private var initializationJob: Job? = null

    fun start() {
        if (initializationJob?.isActive == true) return
        initializationJob =
            scope.launch {
                safely("initialize Apple Gateway") {
                    store.initialize()
                    syncNativeMessageChanges()
                    engine.refreshProjection(nowMillis)
                }

                projectionSignals
                    .onEach {
                        operationMutex.withLock {
                            safely("refresh Apple Gateway projection") {
                                syncNativeMessageChanges()
                                val status = engine.refreshProjection(nowMillis)
                                if (status.readiness == AppleGatewayReadiness.READY && drainCommands()) {
                                    retryScheduler.schedule()
                                }
                            }
                        }
                    }
                    .launchIn(scope)
                radioInterfaceService.currentDeviceAddressFlow
                    .flatMapLatest { address ->
                        if (address.isNullOrBlank()) {
                            emptyFlow()
                        } else {
                            packetRepository.getGatewayHistoryState(emptyList())
                        }
                    }
                    .onEach {
                        operationMutex.withLock {
                            safely("publish Apple Gateway native-text changes") {
                                syncNativeMessageChanges()
                                engine.refreshProjection(nowMillis)
                            }
                        }
                    }
                    .launchIn(scope)
                gatewayRepository.cachedEnvelopes.onEach(::publishInboundOverlays).launchIn(scope)

                processCommands()
            }
    }

    fun processCommands() {
        scope.launch {
            operationMutex.withLock {
                safely("process Apple Gateway commands") {
                    syncNativeMessageChanges()
                    if (drainCommands()) retryScheduler.schedule()
                }
            }
        }
    }

    fun close() {
        retryScheduler.cancel()
        scope.cancel()
    }

    private suspend fun syncNativeMessageChanges() = channelOperationLock.withLock {
        val session = radioInterfaceService.radioSessionState.value
        val databaseDeviceAddress = databaseManager.currentAddress.value
        if (!isCurrentDatabaseSession(session, databaseDeviceAddress)) return@withLock

        val history = packetRepository.getGatewayHistoryState(emptyList()).first()
        if (!isCurrentDatabaseSession(session, databaseDeviceAddress)) return@withLock
        var after = store.readNativeMessageHighWater(history.historyEpoch)
        do {
            val page =
                packetRepository.getGatewayStableMessageChanges(
                    after = after,
                    limit = AppleGatewayContract.MAX_NATIVE_MESSAGE_CHANGE_PAGE_SIZE,
                )
            if (!isCurrentDatabaseSession(session, databaseDeviceAddress)) return@withLock
            page.forEach { change ->
                val identity = change.identity ?: return@forEach
                val from =
                    change.packet.from?.takeIf { it.isNotBlank() && it != DataPacket.ID_LOCAL } ?: return@forEach
                val text = change.packet.text ?: return@forEach
                engine.appendNativeMessageChange(
                    AppleGatewayNativeMessageChange(
                        historyEpoch = history.historyEpoch,
                        changeSequence = change.changeSeq,
                        sourceChannelId = identity.sourceChannelId,
                        sourceMessageId = identity.sourceMessageId,
                        fromNodeId = from,
                        packetId = change.packet.id.toUInt(),
                        text = text,
                        receivedAtMillis = change.receivedAtMillis,
                        originClientMessageId = change.originClientMessageId,
                    ),
                )
            }
            page.maxOfOrNull { it.changeSeq }?.let { after = it }
        } while (page.size == AppleGatewayContract.MAX_NATIVE_MESSAGE_CHANGE_PAGE_SIZE)
    }

    private fun isCurrentDatabaseSession(session: RadioSessionState, databaseDeviceAddress: String?): Boolean =
        session.isConfiguredReady &&
            databaseDeviceAddress == session.selectedDeviceAddress &&
            databaseManager.currentAddress.value == databaseDeviceAddress &&
            radioInterfaceService.radioSessionState.value == session

    /**
     * Bounded mailbox drain. A transient radio rejection leaves the durable command pending; the projection observer
     * invokes this again as soon as the BLE/radio state reaches READY.
     */
    private suspend fun drainCommands(): Boolean =
        drainGatewayCommands(MAX_COMMANDS_PER_WAKE) { engine.processNext(nowMillis) }

    /** Retries only while the exact radio session is still READY; lifecycle observers own recovery otherwise. */
    private suspend fun retryPendingCommandsWhileReady(): Boolean = operationMutex.withLock {
        try {
            syncNativeMessageChanges()
            val status = engine.refreshProjection(nowMillis)
            if (status.readiness != AppleGatewayReadiness.READY) return@withLock false
            drainCommands()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.w(error) { "Unable to retry pending Apple Gateway commands" }
            true
        }
    }

    private suspend fun publishInboundOverlays(envelopes: List<NtsocialCachedEnvelope>) {
        operationMutex.withLock {
            var changed = false
            envelopes
                .asSequence()
                .filter { it.direction == NtsocialEnvelopeDirection.INBOUND }
                .forEach { envelope ->
                    val epoch = envelope.historyEpoch ?: return@forEach
                    val sourceChannelId = envelope.sourceChannelId ?: return@forEach
                    val sourceNodeId =
                        envelope.from?.takeIf { it.isNotBlank() && it != DataPacket.ID_LOCAL } ?: return@forEach
                    val key =
                        listOf(
                            epoch,
                            sourceChannelId,
                            envelope.envelope.headerMsgIdHex,
                            envelope.packetId.toString(),
                            envelope.portNum.toString(),
                        )
                            .joinToString("|")
                    if (key in processedOverlayKeys) return@forEach
                    try {
                        engine.appendNextOverlayIngress(
                            historyEpoch = epoch,
                            payload =
                            AppleGatewayOverlayIngressPayload(
                                sourceChannelId = sourceChannelId,
                                sourceMessageId = envelope.envelope.headerMsgIdHex.uppercase(),
                                sourceNodeId = sourceNodeId,
                                packetId = envelope.packetId.toUInt(),
                                portNumber = envelope.portNum,
                                rawEnvelope = envelope.rawBytes,
                                receivedAtMillis = envelope.cachedAtMillis,
                            ),
                        )
                        rememberOverlay(key)
                        changed = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Logger.w(error) { "Unable to publish one stable Apple Gateway overlay" }
                    }
                }
            if (changed) engine.refreshProjection(nowMillis)
        }
    }

    private fun rememberOverlay(key: String) {
        processedOverlayKeys += key
        while (processedOverlayKeys.size > MAX_PROCESS_OVERLAY_KEYS) {
            processedOverlayKeys.remove(processedOverlayKeys.first())
        }
    }

    private suspend inline fun safely(operation: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.e(error) { "Unable to $operation" }
        }
    }

    private companion object {
        const val ACTIVE_KEY_VERSION = 1
        const val MAX_COMMANDS_PER_WAKE = 64
        const val MAX_PROCESS_OVERLAY_KEYS = 256
        const val MAX_PENDING_RETRY_ATTEMPTS = 3
        const val PENDING_RETRY_BASE_DELAY_MILLIS = 500L

        fun pendingRetryDelayMillis(attempt: Int): Long =
            PENDING_RETRY_BASE_DELAY_MILLIS * (1L shl (attempt - 1).coerceIn(0, MAX_PENDING_RETRY_ATTEMPTS - 1))
    }
}
