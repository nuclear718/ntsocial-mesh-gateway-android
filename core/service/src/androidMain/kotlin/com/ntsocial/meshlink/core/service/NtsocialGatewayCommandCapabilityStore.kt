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

import org.koin.core.annotation.Single
import java.security.SecureRandom
import java.util.Base64

/**
 * Single-use authorization capabilities bridge API 26-33's lack of broadcast-sender identity.
 *
 * On Android 14+ the receiver additionally verifies [NtsocialGatewayCaller] directly. On earlier Android releases, only
 * a Provider caller that passed UID/package/certificate verification can obtain this unguessable, request-bound
 * capability. Explicit broadcasts keep it out of other applications' receivers; expiry and one-time consumption limit
 * replay if a device is compromised.
 */
@Single
internal class NtsocialGatewayCommandCapabilityStore {
    private val random = SecureRandom()
    private val lock = Any()
    private val capabilities = mutableMapOf<String, Capability>()

    fun issue(
        caller: NtsocialGatewayCaller,
        requestId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): IssuedCapability = synchronized(lock) {
        purgeExpired(nowMillis)
        val token = newToken()
        val expiresAtMillis = nowMillis + CAPABILITY_TTL_MILLIS
        capabilities[token] = Capability(caller = caller, requestId = requestId, expiresAtMillis = expiresAtMillis)
        IssuedCapability(token = token, expiresAtMillis = expiresAtMillis)
    }

    fun consume(
        token: String,
        requestId: String,
        senderUid: Int?,
        nowMillis: Long = System.currentTimeMillis(),
    ): NtsocialGatewayCaller? = synchronized(lock) {
        purgeExpired(nowMillis)
        val capability = capabilities.remove(token) ?: return null
        capability.takeIf { it.requestId == requestId && (senderUid == null || it.caller.uid == senderUid) }?.caller
    }

    private fun purgeExpired(nowMillis: Long) {
        capabilities.entries.removeAll { (_, capability) -> capability.expiresAtMillis <= nowMillis }
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_SIZE_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class Capability(val caller: NtsocialGatewayCaller, val requestId: String, val expiresAtMillis: Long)

    data class IssuedCapability(val token: String, val expiresAtMillis: Long)

    private companion object {
        const val CAPABILITY_TTL_MILLIS = 30_000L
        const val TOKEN_SIZE_BYTES = 32
    }
}
