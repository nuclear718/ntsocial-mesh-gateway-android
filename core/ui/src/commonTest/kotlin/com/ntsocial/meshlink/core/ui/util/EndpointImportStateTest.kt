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
package com.ntsocial.meshlink.core.ui.util

import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.util.getChannelUrl
import com.ntsocial.meshlink.core.repository.RadioSessionState
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndpointImportStateTest {
    @Test
    fun fourConcurrentEndpointsKeepIndependentPendingChannels() {
        val imports = (1..4).map { index -> EndpointImportState(1) { readySession(index) } }
        imports.forEachIndexed { index, state -> state.handleUri(channelUri("Radio$index")) { error("Valid URL") } }
        imports.forEachIndexed { index, state ->
            assertEquals("Radio$index", state.channelSet?.settings?.single()?.name)
        }
        imports[1].clearChannelSet()
        assertNull(imports[1].channelSet)
        assertEquals("Radio0", imports[0].channelSet?.settings?.single()?.name)
        assertEquals("Radio2", imports[2].channelSet?.settings?.single()?.name)
        assertEquals("Radio3", imports[3].channelSet?.settings?.single()?.name)
    }

    @Test
    fun reconnectOrUnconfiguredSessionRejectsAnEarlierScan() {
        var session = readySession(1)
        val state = EndpointImportState(session.epoch) { session }
        var invalid = 0
        session = session.copy(epoch = 2)
        state.handleUri(channelUri("Stale")) { invalid++ }
        session = session.copy(epoch = 1, configured = false)
        state.handleUri(channelUri("NotReady")) { invalid++ }
        assertEquals(2, invalid)
        assertNull(state.channelSet)
    }

    @Test
    fun leavingScreenClearsPreviewAndIgnoresRetiredCallback() {
        val state = EndpointImportState(1) { readySession(1) }
        state.handleUri(channelUri("Pending")) { error("Valid URL") }
        state.dispose()
        assertNull(state.channelSet)
        state.handleUri(channelUri("Late")) { error("Disposed screen must not display errors") }
        assertNull(state.channelSet)
    }

    @Test
    fun malformedPayloadReportsErrorWithoutOpeningConfirmation() {
        val state = EndpointImportState(1) { readySession(1) }
        var invalid = 0
        state.handleUri(CommonUri.parse("https://meshtastic.org/e/#invalid!")) { invalid++ }
        assertEquals(1, invalid)
        assertNull(state.channelSet)
    }

    private fun readySession(index: Int) = RadioSessionState(
        epoch = 1,
        selectedDeviceAddress = "radio$index",
        activeDeviceAddress = "radio$index",
        transportConnectionState = ConnectionState.Connected,
        configured = true,
    )

    private fun channelUri(name: String) = CommonUri.parse(
        ChannelSet(settings = listOf(ChannelSettings(name = name))).getChannelUrl(shouldAdd = true).toString(),
    )
}
