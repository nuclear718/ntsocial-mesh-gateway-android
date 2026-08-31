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

import com.ntsocial.meshlink.core.data.manager.MeshConnectionManagerImpl
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSessionFactory
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class IosCompositionRootTest {
    @Test
    @OptIn(ExperimentalForeignApi::class)
    fun gatewayAndRadioGraphResolveTogether() {
        val directory = "${NSTemporaryDirectory()}/meshlink-gateway-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        val root = IosCompositionRoot()
        try {
            val key = ByteArray(32) { index -> index.toByte() }.toByteString().base64()
            root.configureGateway(sharedContainerPath = directory, hmacKeyBase64 = key)
            root.start()
            root.processGatewayCommands()
            root.setHostActive(true)
        } finally {
            root.close()
            NSFileManager.defaultManager.removeItemAtPath(directory, null)
        }
    }

    @Test
    fun secondaryScopeResolvesCompleteConnectionGraphAndFailsGatewayAccessClosed() = runTest {
        val root = IosCompositionRoot()
        val endpointId = RadioEndpointId("secondary-runtime-${NSUUID().UUIDString}")
        val session =
            root.koin
                .get<RadioEndpointSessionFactory>()
                .create(
                    RadioEndpointProfile(
                        id = endpointId,
                        protocol = RadioProtocol.MESHTASTIC,
                        transportAddress = "AA:BB:CC:DD:EE:02",
                        displayName = "Secondary",
                    ),
                )

        try {
            val endpointScope = assertNotNull(root.koin.get<IosRadioEndpointScopeRegistry>().scopes.value[endpointId])
            assertIs<MeshConnectionManagerImpl>(endpointScope.get<MeshConnectionManager>())
            assertIs<IosSecondaryGatewayRepository>(endpointScope.get<NtsocialGatewayRepository>())
        } finally {
            session.close()
            root.close()
        }
    }
}
