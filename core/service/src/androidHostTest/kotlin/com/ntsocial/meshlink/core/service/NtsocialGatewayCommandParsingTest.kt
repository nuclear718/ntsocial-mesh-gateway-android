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

import android.content.Intent
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NtsocialGatewayCommandParsingTest {

    @Test
    fun `command classifier keeps v1 null-only and rejects unknown command types`() {
        assertEquals(GatewayCommandKind.V1, classifyGatewayCommand(null))
        assertEquals(
            GatewayCommandKind.ROUTED_OVERLAY,
            classifyGatewayCommand(NtsocialGatewayContract.COMMAND_SEND_NTSOCIAL_ENVELOPE_TO_ROUTE),
        )
        assertEquals(GatewayCommandKind.UNSUPPORTED, classifyGatewayCommand("SEND_CHANNEL_TEXT"))
    }

    @Test
    fun `routed parser requires exact 32 hex client id and canonicalizes uppercase`() {
        val sourceChannelId = "meshtastic:AbCd"
        val parsed =
            assertNotNull(
                parseGatewayRoutedCommand(
                    validIntent()
                        .putExtra(NtsocialGatewayContract.EXTRA_SOURCE_CHANNEL_ID, sourceChannelId)
                        .putExtra(NtsocialGatewayContract.EXTRA_CLIENT_MESSAGE_ID, "0123456789abcdef0123456789abcdef"),
                ),
            )

        assertEquals("0123456789ABCDEF0123456789ABCDEF", parsed.clientMessageId)
        assertEquals(sourceChannelId, parsed.sourceChannelId)
        assertNull(
            parseGatewayRoutedCommand(
                validIntent().putExtra(NtsocialGatewayContract.EXTRA_CLIENT_MESSAGE_ID, "0123456789ABCDEF"),
            ),
        )
        assertNull(
            parseGatewayRoutedCommand(
                validIntent()
                    .putExtra(NtsocialGatewayContract.EXTRA_CLIENT_MESSAGE_ID, "G123456789ABCDEF0123456789ABCDEF"),
            ),
        )
    }

    private fun validIntent(): Intent = Intent(NtsocialGatewayContract.ACTION_COMMAND)
        .putExtra(NtsocialGatewayContract.EXTRA_REQUEST_ID, "request")
        .putExtra(NtsocialGatewayContract.EXTRA_AUTHORIZATION_TOKEN, "authorization")
        .putExtra(NtsocialGatewayContract.EXTRA_SOURCE_CHANNEL_ID, "meshtastic:source")
        .putExtra(NtsocialGatewayContract.EXTRA_ROUTE_TOKEN, "route")
        .putExtra(NtsocialGatewayContract.EXTRA_CLIENT_MESSAGE_ID, "0123456789ABCDEF0123456789ABCDEF")
        .putExtra(NtsocialGatewayContract.EXTRA_PAYLOAD, byteArrayOf(0x4E, 0x4D))
}
