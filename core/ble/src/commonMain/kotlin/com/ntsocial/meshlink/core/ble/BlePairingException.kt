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
package com.ntsocial.meshlink.core.ble

/** User-actionable failures raised before a protected BLE GATT session can be opened. */
enum class BlePairingFailure {
    DEVICE_NOT_FOUND,
    NOT_READY,
    CANCELED,
    REJECTED,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,
    TIMED_OUT,
    PLATFORM_FAILURE,
}

/**
 * Signals that explicit platform pairing did not complete.
 *
 * Pairing failures are intentionally distinct from connection failures so the reconnect loop does not repeatedly open a
 * PIN prompt or continue into protected GATT characteristics after the user cancels.
 */
class BlePairingException(val failure: BlePairingFailure, message: String, cause: Throwable? = null) :
    Exception(message, cause)
