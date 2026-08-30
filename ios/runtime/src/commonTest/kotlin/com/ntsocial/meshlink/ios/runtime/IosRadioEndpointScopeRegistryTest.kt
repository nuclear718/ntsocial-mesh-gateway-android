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

import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IosRadioEndpointScopeRegistryTest {
    @Test
    fun `stale runtime cannot unregister its replacement`() {
        val application = koinApplication {}
        val registry = IosRadioEndpointScopeRegistry()
        val endpointId = RadioEndpointId("endpoint-a")
        val oldScope = application.koin.createScope("old", named("test"))
        val replacement = application.koin.createScope("replacement", named("test"))

        try {
            registry.register(endpointId, oldScope)
            registry.register(endpointId, replacement)
            registry.unregister(endpointId, oldScope)

            assertEquals(setOf(endpointId), registry.scopes.value.keys)
            assertSame(replacement, registry.scopes.value.getValue(endpointId))

            registry.unregister(endpointId, replacement)
            assertEquals(emptyMap(), registry.scopes.value)
        } finally {
            oldScope.close()
            replacement.close()
            application.close()
        }
    }
}
