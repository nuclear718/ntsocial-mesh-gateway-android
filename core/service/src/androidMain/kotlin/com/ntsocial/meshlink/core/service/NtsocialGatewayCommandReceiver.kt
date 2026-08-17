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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayNativeText
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Explicit command endpoint for a pre-authorized NTsocial Gateway request.
 *
 * Android 14+ supplies the original broadcast sender UID, which is verified again here. API 26-33 does not expose a
 * broadcast sender identity to receivers, so a caller must first obtain a single-use Provider capability after its
 * UID/package/certificate has been validated. This receiver never accepts a package name or a port number as proof of
 * authority. Routed overlay requests never send port 497, while native text requests construct only a normal broadcast
 * TEXT_MESSAGE_APP packet.
 */
@Suppress("TooManyFunctions")
class NtsocialGatewayCommandReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val callerVerifier: NtsocialGatewayCallerVerifier by inject()
    private val capabilityStore: NtsocialGatewayCommandCapabilityStore by inject()
    private val routeTokenStore: NtsocialGatewayRouteTokenStore by inject()
    private val gatewayRepository: NtsocialGatewayRepository by inject()
    private val eventPublisher: NtsocialGatewayEventPublisher by inject()
    private val scope: CoroutineScope by inject(qualifier = named("ServiceScope"))

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NtsocialGatewayContract.ACTION_COMMAND) return

        val broadcastSender = trustedBroadcastSenderOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Logger.i {
                "ntsocial_gateway_tx stage=received senderUid=${getSentFromUid()} " +
                    "senderPackage=${getSentFromPackage() ?: "none"} trusted=${broadcastSender != null}"
            }
        } else {
            Logger.i { "ntsocial_gateway_tx stage=received senderIdentity=capability_only" }
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (classifyGatewayCommand(intent.getStringExtra(NtsocialGatewayContract.EXTRA_COMMAND_TYPE))) {
                    GatewayCommandKind.V1 -> processCommand(intent, broadcastSender)
                    GatewayCommandKind.ROUTED_OVERLAY -> processRoutedOverlayCommand(intent, broadcastSender)
                    GatewayCommandKind.NATIVE_TEXT -> processNativeTextCommand(intent, broadcastSender)
                    GatewayCommandKind.UNSUPPORTED -> processUnsupportedCommand(intent, broadcastSender)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun processNativeTextCommand(intent: Intent, broadcastSender: NtsocialGatewayCaller?) {
        val request = parseGatewayNativeTextCommand(intent) ?: return
        val caller =
            authorizedCallerOrNull(
                requestId = request.requestId,
                authorizationToken = request.authorizationToken,
                broadcastSender = broadcastSender,
            ) ?: return

        val route =
            routeTokenStore.resolve(
                token = request.routeToken,
                caller = caller,
                sourceChannelId = request.sourceChannelId,
                radioGeneration = eventPublisher.catalogSnapshot.value.radioGeneration,
            )
        if (route == null) {
            reject(caller, request.requestId, REASON_INVALID_ROUTE)
            return
        }

        val requestFingerprint = request.requestFingerprint()
        when (
            val reservation =
                routeTokenStore.reserveClientMessage(
                    caller = caller,
                    clientMessageId = request.clientMessageId,
                    requestFingerprint = requestFingerprint,
                )
        ) {
            is NtsocialGatewayRouteTokenStore.ClientMessageReservation.Accepted ->
                eventPublisher.publishCommandAccepted(caller.packageName, request.requestId, reservation.packetId)

            is NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending ->
                dispatchNativeTextCommand(
                    caller = caller,
                    request = request,
                    channelIndex = route.channelIndex,
                    packetId = reservation.packetId,
                    requestFingerprint = requestFingerprint,
                )

            NtsocialGatewayRouteTokenStore.ClientMessageReservation.Conflict ->
                reject(caller, request.requestId, REASON_IDEMPOTENCY_CONFLICT)
        }
    }

    private fun processCommand(intent: Intent, broadcastSender: NtsocialGatewayCaller?) {
        commandRequestOrNull(intent)?.let { request ->
            authorizedCallerOrNull(
                requestId = request.requestId,
                authorizationToken = request.authorizationToken,
                broadcastSender = broadcastSender,
            )
                ?.let { caller ->
                    canonicalChannelIndexOrNull(caller, request)?.let { channelIndex ->
                        outboundCommandOrNull(caller, request)?.let { command ->
                            dispatchCommand(caller, request.requestId, channelIndex, command)
                        }
                    }
                }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun processRoutedOverlayCommand(intent: Intent, broadcastSender: NtsocialGatewayCaller?) {
        val request = parseGatewayRoutedCommand(intent) ?: return
        val caller =
            authorizedCallerOrNull(
                requestId = request.requestId,
                authorizationToken = request.authorizationToken,
                broadcastSender = broadcastSender,
            ) ?: return

        val route =
            routeTokenStore.resolve(
                token = request.routeToken,
                caller = caller,
                sourceChannelId = request.sourceChannelId,
                radioGeneration = eventPublisher.catalogSnapshot.value.radioGeneration,
            )
        if (route == null) {
            reject(caller, request.requestId, REASON_INVALID_ROUTE)
            return
        }
        val command =
            outboundCommandOrNull(
                caller,
                CommandRequest(
                    requestId = request.requestId,
                    authorizationToken = request.authorizationToken,
                    channelIndex = route.channelIndex,
                    payload = request.payload,
                    to = request.to,
                    hopLimit = request.hopLimit,
                    wantAck = request.wantAck,
                ),
            ) ?: return
        val requestFingerprint = request.requestFingerprint()
        when (
            val reservation =
                routeTokenStore.reserveClientMessage(
                    caller = caller,
                    clientMessageId = request.clientMessageId,
                    requestFingerprint = requestFingerprint,
                )
        ) {
            is NtsocialGatewayRouteTokenStore.ClientMessageReservation.Accepted ->
                eventPublisher.publishCommandAccepted(caller.packageName, request.requestId, reservation.packetId)

            is NtsocialGatewayRouteTokenStore.ClientMessageReservation.Pending ->
                dispatchRoutedCommand(
                    caller = caller,
                    request = request,
                    channelIndex = route.channelIndex,
                    command = command,
                    packetId = reservation.packetId,
                    requestFingerprint = requestFingerprint,
                )

            NtsocialGatewayRouteTokenStore.ClientMessageReservation.Conflict ->
                reject(caller, request.requestId, REASON_IDEMPOTENCY_CONFLICT)
        }
    }

    private fun processUnsupportedCommand(intent: Intent, broadcastSender: NtsocialGatewayCaller?) {
        val request = commandRequestOrNull(intent) ?: return
        val caller =
            authorizedCallerOrNull(
                requestId = request.requestId,
                authorizationToken = request.authorizationToken,
                broadcastSender = broadcastSender,
            ) ?: return
        reject(caller, request.requestId, REASON_UNSUPPORTED_COMMAND)
    }

    private fun commandRequestOrNull(intent: Intent): CommandRequest? {
        val requestId =
            intent.getStringExtra(NtsocialGatewayContract.EXTRA_REQUEST_ID)?.takeIf {
                it.isNotBlank() && it.length <= MAX_REQUEST_ID_LENGTH
            }
        val authorizationToken = intent.getStringExtra(NtsocialGatewayContract.EXTRA_AUTHORIZATION_TOKEN)

        return if (requestId == null || authorizationToken == null) {
            null
        } else {
            CommandRequest(
                requestId = requestId,
                authorizationToken = authorizationToken,
                channelIndex = intent.getIntExtra(NtsocialGatewayContract.EXTRA_CHANNEL_INDEX, INVALID_CHANNEL_INDEX),
                payload = intent.getByteArrayExtra(NtsocialGatewayContract.EXTRA_PAYLOAD),
                to = intent.getStringExtra(NtsocialGatewayContract.EXTRA_TO),
                hopLimit = intent.getIntExtra(NtsocialGatewayContract.EXTRA_HOP_LIMIT, DEFAULT_HOP_LIMIT),
                wantAck = intent.getBooleanExtra(NtsocialGatewayContract.EXTRA_WANT_ACK, true),
            )
        }
    }

    private fun authorizedCallerOrNull(
        requestId: String,
        authorizationToken: String,
        broadcastSender: NtsocialGatewayCaller?,
    ): NtsocialGatewayCaller? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && broadcastSender == null) {
            Logger.w { "ntsocial_gateway_tx stage=authorization result=rejected reason=sender_untrusted" }
            return null
        }

        val capabilityCaller = capabilityStore.consume(authorizationToken, requestId, broadcastSender?.uid)
        val authorized = capabilityCaller?.takeIf { broadcastSender == null || broadcastSender == it }
        Logger.i {
            "ntsocial_gateway_tx stage=authorization result=${if (authorized == null) "rejected" else "accepted"} " +
                "senderUid=${broadcastSender?.uid ?: -1} capabilityUid=${capabilityCaller?.uid ?: -1}"
        }
        return authorized
    }

    private fun canonicalChannelIndexOrNull(caller: NtsocialGatewayCaller, request: CommandRequest): Int? {
        val defaultChannel = gatewayRepository.defaultChannelStatus.value

        return when {
            !defaultChannel.ready || defaultChannel.channelIndex == null -> {
                reject(caller, request.requestId, REASON_CHANNEL_NOT_READY)
                null
            }

            request.channelIndex != defaultChannel.channelIndex -> {
                reject(caller, request.requestId, REASON_CHANNEL_MISMATCH)
                null
            }

            else -> defaultChannel.channelIndex
        }
    }

    private fun outboundCommandOrNull(caller: NtsocialGatewayCaller, request: CommandRequest): OutboundCommand? = when {
        request.payload == null ||
            request.payload.isEmpty() ||
            request.payload.size > NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES -> {
            reject(caller, request.requestId, REASON_INVALID_ENVELOPE)
            null
        }

        !request.to.isValidDestination() -> {
            reject(caller, request.requestId, REASON_INVALID_DESTINATION)
            null
        }

        request.hopLimit < MIN_HOP_LIMIT -> {
            reject(caller, request.requestId, REASON_INVALID_HOP_LIMIT)
            null
        }

        else ->
            OutboundCommand(
                rawEnvelope = requireNotNull(request.payload).toByteString(),
                to = request.to,
                hopLimit = request.hopLimit,
                wantAck = request.wantAck,
            )
    }

    private fun dispatchCommand(
        caller: NtsocialGatewayCaller,
        requestId: String,
        channelIndex: Int,
        command: OutboundCommand,
    ) {
        try {
            Logger.i {
                "ntsocial_gateway_tx stage=dispatch requestId=$requestId caller=${caller.packageName} " +
                    "channelIndex=$channelIndex bytes=${command.rawEnvelope.size} wantAck=${command.wantAck}"
            }
            val queued =
                gatewayRepository.sendRawEnvelope(
                    rawEnvelope = command.rawEnvelope,
                    to = command.to,
                    channelIndex = channelIndex,
                    hopLimit = command.hopLimit,
                    wantAck = command.wantAck,
                )
            Logger.i {
                "ntsocial_gateway_tx stage=accepted requestId=$requestId packetId=${queued.packetId} " +
                    "channelIndex=$channelIndex"
            }
            eventPublisher.publishCommandAccepted(caller.packageName, requestId, queued.packetId)
        } catch (_: IllegalArgumentException) {
            Logger.w { "ntsocial_gateway_tx stage=rejected requestId=$requestId reason=$REASON_INVALID_ENVELOPE" }
            reject(caller, requestId, REASON_INVALID_ENVELOPE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Do not log raw payloads, destinations, credentials, or packet contents from an external caller.
            Logger.w { "ntsocial_gateway_tx stage=rejected requestId=$requestId reason=$REASON_QUEUE_FAILED" }
            reject(caller, requestId, REASON_QUEUE_FAILED)
        }
    }

    private suspend fun dispatchRoutedCommand(
        caller: NtsocialGatewayCaller,
        request: GatewayRoutedCommand,
        channelIndex: Int,
        command: OutboundCommand,
        packetId: Int,
        requestFingerprint: String,
    ) {
        try {
            val queued =
                gatewayRepository.persistAndQueueRawEnvelope(
                    rawEnvelope = command.rawEnvelope,
                    sourceChannelId = request.sourceChannelId,
                    to = command.to,
                    channelIndex = channelIndex,
                    hopLimit = command.hopLimit,
                    wantAck = command.wantAck,
                    packetId = packetId,
                )
            val ledgerCommitted =
                routeTokenStore.markClientMessageAccepted(
                    caller = caller,
                    clientMessageId = request.clientMessageId,
                    requestFingerprint = requestFingerprint,
                    packetId = queued.packetId,
                )
            if (!ledgerCommitted) {
                Logger.w { "ntsocial_gateway_tx stage=ledger result=pending_commit_failed" }
                reject(caller, request.requestId, REASON_QUEUE_FAILED)
                return
            }
            eventPublisher.publishCommandAccepted(caller.packageName, request.requestId, queued.packetId)
        } catch (_: IllegalArgumentException) {
            reject(caller, request.requestId, REASON_INVALID_ENVELOPE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            reject(caller, request.requestId, REASON_QUEUE_FAILED)
        }
    }

    private suspend fun dispatchNativeTextCommand(
        caller: NtsocialGatewayCaller,
        request: GatewayNativeTextCommand,
        channelIndex: Int,
        packetId: Int,
        requestFingerprint: String,
    ) {
        try {
            val packet =
                gatewayRepository.persistAndQueueNativeBroadcastText(
                    text = request.text,
                    sourceChannelId = request.sourceChannelId,
                    channelIndex = channelIndex,
                    packetId = packetId,
                    originClientMessageId = request.clientMessageId,
                )
            val ledgerCommitted =
                routeTokenStore.markClientMessageAccepted(
                    caller = caller,
                    clientMessageId = request.clientMessageId,
                    requestFingerprint = requestFingerprint,
                    packetId = packet.id,
                )
            if (!ledgerCommitted) {
                Logger.w { "ntsocial_gateway_native_text stage=ledger result=pending_commit_failed" }
                reject(caller, request.requestId, REASON_QUEUE_FAILED)
                return
            }
            eventPublisher.publishCommandAccepted(caller.packageName, request.requestId, packet.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Native message text is private user content and must never be included in logs.
            reject(caller, request.requestId, REASON_QUEUE_FAILED)
        }
    }

    private fun reject(caller: NtsocialGatewayCaller, requestId: String, reason: String) {
        eventPublisher.publishCommandRejected(caller.packageName, requestId, reason)
    }

    private fun trustedBroadcastSenderOrNull(): NtsocialGatewayCaller? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            callerVerifier.trustedCaller(getSentFromUid(), getSentFromPackage())
        } else {
            null
        }

    private fun String?.isValidDestination(): Boolean =
        this == null || this == DataPacket.ID_BROADCAST || NODE_ID_REGEX.matches(this)

    private data class CommandRequest(
        val requestId: String,
        val authorizationToken: String,
        val channelIndex: Int,
        val payload: ByteArray?,
        val to: String?,
        val hopLimit: Int,
        val wantAck: Boolean,
    )

    private data class OutboundCommand(
        val rawEnvelope: ByteString,
        val to: String?,
        val hopLimit: Int,
        val wantAck: Boolean,
    )

    private companion object {
        const val MAX_REQUEST_ID_LENGTH = 128
        const val INVALID_CHANNEL_INDEX = -1
        const val DEFAULT_HOP_LIMIT = 0
        const val MIN_HOP_LIMIT = 0

        const val REASON_CHANNEL_NOT_READY = "channel_not_ready"
        const val REASON_CHANNEL_MISMATCH = "channel_mismatch"
        const val REASON_INVALID_ENVELOPE = "invalid_envelope"
        const val REASON_INVALID_DESTINATION = "invalid_destination"
        const val REASON_INVALID_HOP_LIMIT = "invalid_hop_limit"
        const val REASON_QUEUE_FAILED = "queue_failed"
        const val REASON_INVALID_ROUTE = "invalid_route"
        const val REASON_UNSUPPORTED_COMMAND = "unsupported_command"
        const val REASON_IDEMPOTENCY_CONFLICT = "idempotency_conflict"

        val NODE_ID_REGEX = Regex("^![0-9a-fA-F]{8}$")
    }
}

internal enum class GatewayCommandKind {
    V1,
    ROUTED_OVERLAY,
    NATIVE_TEXT,
    UNSUPPORTED,
}

internal fun classifyGatewayCommand(commandType: String?): GatewayCommandKind = when (commandType) {
    null -> GatewayCommandKind.V1
    NtsocialGatewayContract.COMMAND_SEND_NTSOCIAL_ENVELOPE_TO_ROUTE -> GatewayCommandKind.ROUTED_OVERLAY
    NtsocialGatewayContract.COMMAND_SEND_CHANNEL_TEXT -> GatewayCommandKind.NATIVE_TEXT
    else -> GatewayCommandKind.UNSUPPORTED
}

internal data class GatewayRoutedCommand(
    val requestId: String,
    val authorizationToken: String,
    val sourceChannelId: String,
    val routeToken: String,
    val clientMessageId: String,
    val payload: ByteArray?,
    val to: String?,
    val hopLimit: Int,
    val wantAck: Boolean,
)

internal data class GatewayNativeTextCommand(
    val requestId: String,
    val authorizationToken: String,
    val sourceChannelId: String,
    val routeToken: String,
    val clientMessageId: String,
    val text: String,
)

@Suppress("ReturnCount")
internal fun parseGatewayRoutedCommand(intent: Intent): GatewayRoutedCommand? {
    val requestId =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_REQUEST_ID)?.takeIf {
            it.isNotBlank() && it.length <= MAX_GATEWAY_REQUEST_ID_LENGTH
        } ?: return null
    val authorizationToken = intent.getStringExtra(NtsocialGatewayContract.EXTRA_AUTHORIZATION_TOKEN) ?: return null
    val sourceChannelId =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_SOURCE_CHANNEL_ID)?.takeIf {
            it.isNotBlank() && it.length <= MAX_SOURCE_CHANNEL_ID_LENGTH
        } ?: return null
    val routeToken =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_ROUTE_TOKEN)?.takeIf {
            it.isNotBlank() && it.length <= MAX_ROUTE_TOKEN_LENGTH
        } ?: return null
    val clientMessageId =
        intent
            .getStringExtra(NtsocialGatewayContract.EXTRA_CLIENT_MESSAGE_ID)
            ?.takeIf(CLIENT_MESSAGE_ID_REGEX::matches)
            ?.uppercase() ?: return null
    return GatewayRoutedCommand(
        requestId = requestId,
        authorizationToken = authorizationToken,
        sourceChannelId = sourceChannelId,
        routeToken = routeToken,
        clientMessageId = clientMessageId,
        payload = intent.getByteArrayExtra(NtsocialGatewayContract.EXTRA_PAYLOAD),
        to = intent.getStringExtra(NtsocialGatewayContract.EXTRA_TO),
        hopLimit = intent.getIntExtra(NtsocialGatewayContract.EXTRA_HOP_LIMIT, DEFAULT_GATEWAY_HOP_LIMIT),
        wantAck = intent.getBooleanExtra(NtsocialGatewayContract.EXTRA_WANT_ACK, true),
    )
}

@Suppress("ReturnCount")
internal fun parseGatewayNativeTextCommand(intent: Intent): GatewayNativeTextCommand? {
    val requestId =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_REQUEST_ID)?.takeIf {
            it.isNotBlank() && it.length <= MAX_GATEWAY_REQUEST_ID_LENGTH
        } ?: return null
    val authorizationToken = intent.getStringExtra(NtsocialGatewayContract.EXTRA_AUTHORIZATION_TOKEN) ?: return null
    val sourceChannelId =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_SOURCE_CHANNEL_ID)?.takeIf {
            it.isNotBlank() && it.length <= MAX_SOURCE_CHANNEL_ID_LENGTH
        } ?: return null
    val routeToken =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_ROUTE_TOKEN)?.takeIf {
            it.isNotBlank() && it.length <= MAX_ROUTE_TOKEN_LENGTH
        } ?: return null
    val clientMessageId =
        intent
            .getStringExtra(NtsocialGatewayContract.EXTRA_CLIENT_MESSAGE_ID)
            ?.takeIf(CLIENT_MESSAGE_ID_REGEX::matches)
            ?.uppercase() ?: return null
    val text =
        intent.getStringExtra(NtsocialGatewayContract.EXTRA_TEXT)?.takeIf(NtsocialGatewayNativeText::isValid)
            ?: return null
    return GatewayNativeTextCommand(
        requestId = requestId,
        authorizationToken = authorizationToken,
        sourceChannelId = sourceChannelId,
        routeToken = routeToken,
        clientMessageId = clientMessageId,
        text = text,
    )
}

private fun GatewayRoutedCommand.requestFingerprint(): String = Buffer()
    .apply {
        writeUtf8(sourceChannelId)
        writeByte(0)
        payload?.let(::write)
        writeByte(0)
        writeUtf8(to.orEmpty())
        writeInt(hopLimit)
        writeByte(if (wantAck) 1 else 0)
    }
    .readByteString()
    .sha256()
    .hex()

internal fun GatewayNativeTextCommand.requestFingerprint(): String = Buffer()
    .apply {
        writeUtf8(NtsocialGatewayContract.COMMAND_SEND_CHANNEL_TEXT)
        writeByte(0)
        writeUtf8(sourceChannelId)
        writeByte(0)
        writeUtf8(text)
    }
    .readByteString()
    .sha256()
    .hex()

private const val MAX_GATEWAY_REQUEST_ID_LENGTH = 128
private const val MAX_SOURCE_CHANNEL_ID_LENGTH = 128
private const val MAX_ROUTE_TOKEN_LENGTH = 128
private const val DEFAULT_GATEWAY_HOP_LIMIT = 0
private val CLIENT_MESSAGE_ID_REGEX = Regex("^[0-9A-Fa-f]{${NtsocialGatewayContract.CLIENT_MESSAGE_ID_HEX_LENGTH}}$")
