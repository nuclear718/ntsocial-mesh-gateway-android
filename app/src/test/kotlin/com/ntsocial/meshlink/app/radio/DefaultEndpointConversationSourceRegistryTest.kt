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
package com.ntsocial.meshlink.app.radio

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSnapshot
import com.ntsocial.meshlink.core.radiofleet.conversation.EndpointConversationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultEndpointConversationSourceRegistryTest {
    @Test
    fun `late unregister from old runtime cannot remove replacement`() {
        val registry = DefaultEndpointConversationSourceRegistry()
        val endpointId = RadioEndpointId("endpoint-a")
        val oldSource = FakeSource(endpointId)
        val replacement = FakeSource(endpointId)

        registry.register("old-runtime", oldSource)
        registry.register("new-runtime", replacement)
        registry.unregister(endpointId, "old-runtime")

        val entry = registry.entries.value.getValue(endpointId)
        assertEquals("new-runtime", entry.runtimeToken)
        assertTrue(entry.source === replacement)
    }

    private class FakeSource(override val endpointId: RadioEndpointId) : EndpointConversationSource {
        override val snapshot = MutableStateFlow(EndpointConversationSnapshot(endpointId))
    }
}
