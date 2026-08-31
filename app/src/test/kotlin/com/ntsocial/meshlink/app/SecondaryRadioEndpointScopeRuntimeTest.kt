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
package com.ntsocial.meshlink.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ntsocial.meshlink.app.radio.RadioEndpointScopeRegistry
import com.ntsocial.meshlink.app.radio.SecondaryGatewayRepository
import com.ntsocial.meshlink.core.data.manager.MeshConnectionManagerImpl
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSessionFactory
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecondaryRadioEndpointScopeRuntimeTest {
    @Test
    fun `secondary scope resolves the complete connection graph and fails gateway access closed`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        AndroidKoinBootstrap.ensureStarted(application)
        val koin = GlobalContext.get()
        val endpointId = RadioEndpointId("secondary-runtime-graph")
        val session =
            koin
                .get<RadioEndpointSessionFactory>()
                .create(
                    RadioEndpointProfile(
                        id = endpointId,
                        protocol = RadioProtocol.MESHTASTIC,
                        transportAddress = "xAA:BB:CC:DD:EE:02",
                        displayName = "Secondary",
                    ),
                )

        try {
            val endpointScope = assertNotNull(koin.get<RadioEndpointScopeRegistry>().get(endpointId))
            assertIs<MeshConnectionManagerImpl>(endpointScope.get<MeshConnectionManager>())
            assertIs<SecondaryGatewayRepository>(endpointScope.get<NtsocialGatewayRepository>())
        } finally {
            session.close()
        }
    }
}
