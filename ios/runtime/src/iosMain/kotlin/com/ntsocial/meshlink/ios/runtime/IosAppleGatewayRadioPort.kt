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

import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayNativeTextAdmission
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayOverlayAdmission
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRadioAdmissionResult
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRadioChannelIdentity
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRadioPort
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRadioSnapshot
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayReadiness
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRejectionReason
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayRouteCapability
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayStore
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import kotlin.concurrent.Volatile

/** Narrow adapter from the Apple mailbox engine to the existing KMP Meshtastic repositories. */
internal class IosAppleGatewayRadioPort(
    private val store: AppleGatewayStore,
    private val bluetoothRepository: BluetoothRepository,
    private val serviceRepository: ServiceRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val databaseManager: DatabaseManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val gatewayRepository: NtsocialGatewayRepository,
    private val channelOperationLock: ChannelOperationLock,
) : AppleGatewayRadioPort {
    @Volatile private var projectedSessionGuard: IosGatewayRadioSessionGuard? = null

    @Volatile private var projectedChannelSnapshotGeneration: Long? = null

    override suspend fun snapshot(): AppleGatewayRadioSnapshot = channelOperationLock.withLock {
        val snapshotGeneration = radioConfigRepository.channelSnapshotGeneration.value
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val channels = channelSet.toGatewayChannels()
        val guard = currentSessionGuard(channelSet)
        var readiness = guard.readiness(channels.isNotEmpty())
        var history =
            if (
                readiness == AppleGatewayReadiness.READY &&
                !snapshotGeneration.isMutationInFlight() &&
                radioConfigRepository.channelSnapshotGeneration.value == snapshotGeneration &&
                gatewayRepository.isInboundSessionActive(guard.sessionEpoch)
            ) {
                packetRepository.readCurrentGatewayHistoryState(emptyList())
            } else {
                if (readiness == AppleGatewayReadiness.READY) readiness = AppleGatewayReadiness.CONFIGURING
                null
            }
        if (
            history != null &&
            (
                radioConfigRepository.channelSnapshotGeneration.value != snapshotGeneration ||
                    currentSessionGuard(channelSet) != guard ||
                    !gatewayRepository.isInboundSessionActive(guard.sessionEpoch)
                )
        ) {
            readiness = AppleGatewayReadiness.CONFIGURING
            history = null
        }
        if (readiness == AppleGatewayReadiness.READY) {
            projectedSessionGuard = guard
            projectedChannelSnapshotGeneration = snapshotGeneration
        }
        val historyEpoch = history?.historyEpoch
        AppleGatewayRadioSnapshot(
            readiness = readiness,
            channels = channels,
            historyEpoch = historyEpoch,
            overlayHighWater = historyEpoch?.let { store.readOverlayHighWater(it) } ?: 0,
            nativeTextHighWater = historyEpoch?.let { store.readNativeMessageHighWater(it) } ?: 0,
            routingContext = guard.routingContext(snapshotGeneration),
        )
    }

    override suspend fun durablyAdmitOverlay(
        admission: AppleGatewayOverlayAdmission,
    ): AppleGatewayRadioAdmissionResult = admit(admission.sourceChannelId, admission.capturedSlotIndex) {
        gatewayRepository.persistAndQueueRawEnvelope(
            rawEnvelope = admission.rawEnvelope,
            sourceChannelId = admission.sourceChannelId,
            to = admission.destination,
            channelIndex = admission.capturedSlotIndex,
            hopLimit = admission.hopLimit,
            wantAck = admission.wantAck,
            packetId = admission.packetId,
        )
    }

    override suspend fun durablyAdmitNativeText(
        admission: AppleGatewayNativeTextAdmission,
    ): AppleGatewayRadioAdmissionResult = admit(admission.sourceChannelId, admission.capturedSlotIndex) {
        gatewayRepository.persistAndQueueNativeBroadcastText(
            text = admission.text,
            sourceChannelId = admission.sourceChannelId,
            channelIndex = admission.capturedSlotIndex,
            packetId = admission.packetId,
            originClientMessageId = admission.canonicalClientMessageId,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun admit(
        sourceChannelId: String,
        slotIndex: Int,
        block: suspend () -> Unit,
    ): AppleGatewayRadioAdmissionResult {
        return channelOperationLock.withLock {
            val channelSet = radioConfigRepository.channelSetFlow.first()
            val expectedGuard =
                projectedSessionGuard
                    ?: return@withLock AppleGatewayRadioAdmissionResult.TransientFailure(
                        AppleGatewayRejectionReason.RADIO_NOT_READY,
                    )
            val expectedSnapshotGeneration =
                projectedChannelSnapshotGeneration
                    ?: return@withLock AppleGatewayRadioAdmissionResult.TransientFailure(
                        AppleGatewayRejectionReason.RADIO_NOT_READY,
                    )
            val currentGuard = currentSessionGuard(channelSet)
            val currentSnapshotGeneration = radioConfigRepository.channelSnapshotGeneration.value
            if (currentGuard != expectedGuard || currentSnapshotGeneration != expectedSnapshotGeneration) {
                return@withLock AppleGatewayRadioAdmissionResult.PermanentFailure(
                    AppleGatewayRejectionReason.INVALID_ROUTE,
                )
            }
            if (
                currentSnapshotGeneration.isMutationInFlight() ||
                currentGuard.readiness(channelSet.settings.isNotEmpty()) != AppleGatewayReadiness.READY ||
                !gatewayRepository.isInboundSessionActive(currentGuard.sessionEpoch)
            ) {
                return@withLock AppleGatewayRadioAdmissionResult.TransientFailure(
                    AppleGatewayRejectionReason.RADIO_NOT_READY,
                )
            }
            if (channelSet.sourceChannelId(slotIndex) != sourceChannelId) {
                return@withLock AppleGatewayRadioAdmissionResult.PermanentFailure(
                    AppleGatewayRejectionReason.INVALID_ROUTE,
                )
            }
            try {
                block()
                AppleGatewayRadioAdmissionResult.Accepted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                AppleGatewayRadioAdmissionResult.PermanentFailure(AppleGatewayRejectionReason.RADIO_REJECTED)
            } catch (_: Exception) {
                AppleGatewayRadioAdmissionResult.TransientFailure(AppleGatewayRejectionReason.QUEUE_FAILED)
            }
        }
    }

    private fun currentSessionGuard(channelSet: ChannelSet): IosGatewayRadioSessionGuard = iosGatewayRadioSessionGuard(
        session = radioInterfaceService.radioSessionState.value,
        databaseDeviceAddress = databaseManager.currentAddress.value,
        bluetooth = bluetoothRepository.state.value,
        appConnectionState = serviceRepository.connectionState.value,
        channelSetFingerprint = ChannelSet.ADAPTER.encode(channelSet).toByteString().sha256(),
    )
}

private fun IosGatewayRadioSessionGuard.routingContext(channelSnapshotGeneration: Long) =
    Buffer().write(routingContext()).writeLong(channelSnapshotGeneration).readByteString().sha256()

private fun Long.isMutationInFlight(): Boolean = this and 1L != 0L

private fun ChannelSet.toGatewayChannels(): List<AppleGatewayRadioChannelIdentity> {
    val lora = lora_config ?: Config.LoRaConfig()
    return settings.mapIndexed { index, settings ->
        val role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY
        val identity = NtsocialGatewayIdentity.channel(Channel(index = index, role = role, settings = settings), lora)
        AppleGatewayRadioChannelIdentity(
            sourceChannelId = identity.sourceChannelId,
            slotIndex = index,
            displayName = identity.displayName,
            role = role.name,
            securityClass = identity.securityClass,
            capabilities =
            setOf(
                AppleGatewayRouteCapability.SEND_NTSOCIAL_ENVELOPE,
                AppleGatewayRouteCapability.SEND_NATIVE_BROADCAST_TEXT,
            ),
        )
    }
}

private fun ChannelSet.sourceChannelId(slotIndex: Int): String? {
    val settings = settings.getOrNull(slotIndex) ?: return null
    val role = if (slotIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY
    return NtsocialGatewayIdentity.channel(
        Channel(index = slotIndex, role = role, settings = settings),
        lora_config ?: Config.LoRaConfig(),
    )
        .sourceChannelId
}
