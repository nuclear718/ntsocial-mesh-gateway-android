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
package com.ntsocial.meshlink.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Explicit command endpoint for a pre-authorized raw NTsocial envelope.
 *
 * Android 14+ supplies the original broadcast sender UID, which is verified again here. API 26-33 does not expose a
 * broadcast sender identity to receivers, so a caller must first obtain a single-use Provider capability after its
 * UID/package/certificate has been validated. This receiver never accepts a package name or a port number as proof of
 * authority, and it never sends port 497.
 */
class NtsocialGatewayCommandReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val callerVerifier: NtsocialGatewayCallerVerifier by inject()
    private val capabilityStore: NtsocialGatewayCommandCapabilityStore by inject()
    private val gatewayRepository: NtsocialGatewayRepository by inject()
    private val eventPublisher: NtsocialGatewayEventPublisher by inject()
    private val scope: CoroutineScope by inject(qualifier = named("ServiceScope"))

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NtsocialGatewayContract.ACTION_COMMAND) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                processCommand(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun processCommand(intent: Intent) {
        commandRequestOrNull(intent)?.let { request ->
            authorizedCallerOrNull(request)?.let { caller ->
                canonicalChannelIndexOrNull(caller, request)?.let { channelIndex ->
                    outboundCommandOrNull(caller, request)?.let { command ->
                        dispatchCommand(caller, request.requestId, channelIndex, command)
                    }
                }
            }
        }
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

    private fun authorizedCallerOrNull(request: CommandRequest): NtsocialGatewayCaller? {
        val sender = trustedBroadcastSenderOrNull()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && sender == null) {
            null
        } else {
            capabilityStore.consume(
                request.authorizationToken,
                request.requestId,
                sender?.uid,
            )?.takeIf { capabilityCaller ->
                sender == null || sender == capabilityCaller
            }
        }
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
            val queued =
                gatewayRepository.sendRawEnvelope(
                    rawEnvelope = command.rawEnvelope,
                    to = command.to,
                    channelIndex = channelIndex,
                    hopLimit = command.hopLimit,
                    wantAck = command.wantAck,
                )
            eventPublisher.publishCommandAccepted(caller.packageName, requestId, queued.packetId)
        } catch (_: IllegalArgumentException) {
            reject(caller, requestId, REASON_INVALID_ENVELOPE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Do not log raw payloads, destinations, credentials, or packet contents from an external caller.
            reject(caller, requestId, REASON_QUEUE_FAILED)
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

        val NODE_ID_REGEX = Regex("^![0-9a-fA-F]{8}$")
    }
}
