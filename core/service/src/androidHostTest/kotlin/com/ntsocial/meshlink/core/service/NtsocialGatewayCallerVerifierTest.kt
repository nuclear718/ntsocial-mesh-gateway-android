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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NtsocialGatewayCallerVerifierTest {
    @Test
    fun `pinned debug and release clients are accepted by either MeshLink build type`() {
        listOf(true, false).forEach { hostIsDebuggable ->
            assertTrue(
                NtsocialGatewayClientTrust.isTrusted(
                    NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                    signingIdentity(NtsocialGatewayClientTrust.TEAM_DEBUG_CERTIFICATE_SHA256),
                    signingIdentity(HOST_SIGNER),
                    hostIsDebuggable,
                ),
                "hostIsDebuggable=$hostIsDebuggable",
            )
            assertTrue(
                NtsocialGatewayClientTrust.isTrusted(
                    NtsocialGatewayClientTrust.RELEASE_PACKAGE,
                    signingIdentity(NtsocialGatewayClientTrust.RELEASE_CERTIFICATE_SHA256),
                    signingIdentity(HOST_SIGNER),
                    hostIsDebuggable,
                ),
                "hostIsDebuggable=$hostIsDebuggable",
            )
        }
    }

    @Test
    fun `fixed pins accept an approved signer in a valid signing history`() {
        assertTrue(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.RELEASE_PACKAGE,
                GatewayPackageSigningIdentity(
                    currentSignerDigests = setOf(ROTATED_CLIENT_SIGNER),
                    signingHistoryDigests =
                    setOf(NtsocialGatewayClientTrust.RELEASE_CERTIFICATE_SHA256, ROTATED_CLIENT_SIGNER),
                ),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = false,
            ),
        )
    }

    @Test
    fun `debug host accepts a same-signed local debug client`() {
        assertTrue(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                signingIdentity(HOST_SIGNER),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = true,
            ),
        )
    }

    @Test
    fun `release host rejects a same-signed local debug client`() {
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                signingIdentity(HOST_SIGNER),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = false,
            ),
        )
    }

    @Test
    fun `same-signer debug trust remains bound to the debug client package`() {
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.RELEASE_PACKAGE,
                signingIdentity(HOST_SIGNER),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = true,
            ),
        )
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                "com.example.fake.ntsocial",
                signingIdentity(HOST_SIGNER),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = true,
            ),
        )
    }

    @Test
    fun `same-signer debug trust requires matching current signers`() {
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                GatewayPackageSigningIdentity(
                    currentSignerDigests = setOf(ROTATED_CLIENT_SIGNER),
                    signingHistoryDigests = setOf(HOST_SIGNER, ROTATED_CLIENT_SIGNER),
                ),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = true,
            ),
        )
    }

    @Test
    fun `same-signer debug trust rejects partially overlapping multi-signer sets`() {
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                signingIdentity(HOST_SIGNER, CLIENT_SECOND_SIGNER),
                signingIdentity(HOST_SIGNER, HOST_SECOND_SIGNER),
                hostIsDebuggable = true,
            ),
        )
    }

    @Test
    fun `package and pinned signer must match the same approved client identity`() {
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                signingIdentity(NtsocialGatewayClientTrust.RELEASE_CERTIFICATE_SHA256),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = false,
            ),
        )
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.RELEASE_PACKAGE,
                signingIdentity(NtsocialGatewayClientTrust.TEAM_DEBUG_CERTIFICATE_SHA256),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = true,
            ),
        )
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                "com.example.fake.ntsocial",
                signingIdentity(NtsocialGatewayClientTrust.TEAM_DEBUG_CERTIFICATE_SHA256),
                signingIdentity(HOST_SIGNER),
                hostIsDebuggable = true,
            ),
        )
        assertFalse(
            NtsocialGatewayClientTrust.isTrusted(
                NtsocialGatewayClientTrust.DEBUG_PACKAGE,
                signingIdentity(),
                signingIdentity(),
                hostIsDebuggable = true,
            ),
        )
    }

    private fun signingIdentity(vararg digests: String) =
        GatewayPackageSigningIdentity(currentSignerDigests = digests.toSet())

    private companion object {
        const val HOST_SIGNER = "LOCAL_DEBUG_HOST_SIGNER"
        const val ROTATED_CLIENT_SIGNER = "ROTATED_LOCAL_DEBUG_CLIENT_SIGNER"
        const val CLIENT_SECOND_SIGNER = "LOCAL_DEBUG_CLIENT_SECOND_SIGNER"
        const val HOST_SECOND_SIGNER = "LOCAL_DEBUG_HOST_SECOND_SIGNER"
    }
}
