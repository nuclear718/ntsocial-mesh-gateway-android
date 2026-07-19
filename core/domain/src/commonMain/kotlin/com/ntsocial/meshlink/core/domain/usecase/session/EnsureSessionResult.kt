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
package com.ntsocial.meshlink.core.domain.usecase.session

/**
 * Transient outcome of a single call to [EnsureRemoteAdminSessionUseCase]. This is the *event* the UI reacts to
 * (snackbar / navigate / disable button) — distinct from the durable `SessionStatus` flow used by chips and gates.
 */
sealed interface EnsureSessionResult {
    /** A fresh session was already on file; no admin packet was sent. */
    data object AlreadyActive : EnsureSessionResult

    /** A metadata request was dispatched and a passkey-bearing response was observed within the UX deadline. */
    data object Refreshed : EnsureSessionResult

    /**
     * The metadata request was dispatched but no response arrived within the UX deadline. The request is still in
     * flight and a late response will still update the durable `SessionStatus` flow.
     */
    data object Timeout : EnsureSessionResult

    /** The radio is not in [com.ntsocial.meshlink.core.model.ConnectionState.Connected]; no packet was sent. */
    data object Disconnected : EnsureSessionResult
}
