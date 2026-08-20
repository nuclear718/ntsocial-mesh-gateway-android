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
package com.ntsocial.meshlink.core.repository

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.model.InterfaceId
import com.ntsocial.meshlink.core.model.MeshActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the low-level radio interface that handles raw byte communication.
 *
 * This is the **transport layer** — it manages the raw hardware connection (BLE, TCP, Serial, USB) to a Meshtastic
 * radio. Its [connectionState] reflects whether the physical link is up or down, **before** any handshake or
 * config-loading logic is applied.
 *
 * **Important:** UI and feature modules should **never** observe [connectionState] directly. Instead, they should use
 * [ServiceRepository.connectionState], which is the canonical app-level connection state that accounts for handshake
 * progress, light-sleep policy, and other higher-level concerns. The only legitimate consumer of this transport-level
 * flow is [MeshConnectionManager], which bridges transport state changes into the app-level
 * [ServiceRepository.connectionState].
 *
 * @see ServiceRepository.connectionState
 */
@Suppress("TooManyFunctions")
interface RadioInterfaceService : RadioTransportCallback {
    /** The device types supported by this platform's radio interface. */
    val supportedDeviceTypes: List<DeviceType>

    /**
     * Transport-level connection state of the radio hardware.
     *
     * This flow reflects the raw state of the physical link (BLE, TCP, Serial, USB):
     * - [ConnectionState.Connected] — the transport link is established
     * - [ConnectionState.Disconnected] — the transport link is down (permanent)
     * - [ConnectionState.DeviceSleep] — the transport link is down (transient, device sleeping)
     *
     * **This is NOT the canonical app-level connection state.** The transport may report [ConnectionState.Connected]
     * while the app is still performing the mesh handshake (config + node-info exchange), during which the app-level
     * state remains [ConnectionState.Connecting].
     *
     * Only [MeshConnectionManager] should observe this flow. All other consumers (ViewModels, feature modules, UI) must
     * use [ServiceRepository.connectionState].
     *
     * @see ServiceRepository.connectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /** Flow of the current device address. */
    val currentDeviceAddressFlow: StateFlow<String?>

    /** Atomic selected/active/configured transport-session facts for fail-closed integration boundaries. */
    val radioSessionState: StateFlow<RadioSessionState>

    /**
     * Marks [expectedEpoch] configured only if it is still the active selected transport session.
     *
     * Returns true only when the exact epoch is current (including an idempotent repeat for an already-configured
     * current session). Callers must stop every handshake-completion side effect when this returns false.
     */
    fun markCurrentSessionConfigured(expectedEpoch: Long): Boolean

    /** Whether we are currently using a mock transport. */
    fun isMockTransport(): Boolean

    /**
     * Flow of raw data received from the radio.
     *
     * Emissions preserve the order in which bytes arrived from the hardware — this is required because the firmware
     * handshake (initial config packet ordering) depends on strict FIFO delivery. Implementations MUST guarantee
     * ordering; do not swap in a [SharedFlow] without preserving order.
     */
    val receivedData: Flow<ByteArray>

    /** Flow of radio activity events. */
    val meshActivity: Flow<MeshActivity>

    /**
     * Drains any bytes currently buffered in [receivedData] without emitting them to collectors.
     *
     * Callers invoke this before attaching a fresh collector after a stop/start cycle so stale bytes buffered while no
     * collector was attached do not get replayed ahead of the next session's handshake.
     */
    fun resetReceivedBuffer()

    /** Sends a raw byte array to the radio. */
    fun sendToRadio(bytes: ByteArray)

    /**
     * Sends only when [expectedRadioSessionEpoch] is still the exact configured owner of the current transport.
     * Implementations that cannot linearize this check with transport replacement must fail closed.
     */
    fun sendToRadioForSession(bytes: ByteArray, expectedRadioSessionEpoch: Long): Boolean = false

    /**
     * Runs one synchronous local projection only while [expectedRadioSessionEpoch] is still the configured active
     * transport session. Implementations must linearize this check and [block] with selection/session rotation using
     * the same lock as [sendToRadioForSession].
     */
    fun runIfCurrentRadioSession(expectedRadioSessionEpoch: Long, block: () -> Unit): Boolean = false

    /** Initiates the connection to the radio. */
    fun connect()

    /**
     * Hydrates the authoritative persisted radio selection before the per-radio database is chosen. Must be called
     * before [connect]; it never starts a transport.
     */
    suspend fun awaitHydratedDeviceAddress(): String? = getDeviceAddress()

    /**
     * Explicitly tears down the active transport, sending a polite `ToRadio(disconnect = true)` goodbye frame first
     * when a transport is live. Safe to call when nothing is connected — implementations must no-op in that case.
     * Suspends until the teardown completes.
     */
    suspend fun disconnect()

    /** Returns the current device address. */
    fun getDeviceAddress(): String?

    /** Sets the device address to connect to. */
    fun setDeviceAddress(deviceAddr: String?): Boolean

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Flow of user-facing connection error messages (e.g. permission failures). */
    val connectionError: Flow<String>

    /** The scope in which interface-related coroutines should run. */
    val serviceScope: CoroutineScope
}
