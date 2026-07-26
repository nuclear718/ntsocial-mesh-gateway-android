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

import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NtsocialGatewayProviderQueryTest {

    @Test
    fun `provider query parser accepts only bounded unambiguous decimal cursors`() {
        assertEquals(
            GatewayMessageChangesQuery(after = 7, limit = 200),
            parseGatewayMessageChangesQuery(
                Uri.parse("content://com.ntsocial.meshlink.gateway/v2/message-changes?after=7&limit=200"),
            ),
        )
        assertEquals(
            GatewayMessageChangesQuery(after = 0, limit = 100),
            parseGatewayMessageChangesQuery(Uri.parse("content://com.ntsocial.meshlink.gateway/v2/message-changes")),
        )

        listOf("after=-1", "after=not-a-number", "limit=0", "limit=201", "limit=abc", "unknown=1", "after=1&after=2")
            .forEach { query ->
                assertFailsWith<IllegalArgumentException> {
                    parseGatewayMessageChangesQuery(
                        Uri.parse("content://com.ntsocial.meshlink.gateway/v2/message-changes?$query"),
                    )
                }
            }
    }

    @Test
    fun `legacy scan budget is explicitly bounded`() {
        assertEquals(1_000, MAX_GATEWAY_LEGACY_SCAN_ROWS)
    }
}
