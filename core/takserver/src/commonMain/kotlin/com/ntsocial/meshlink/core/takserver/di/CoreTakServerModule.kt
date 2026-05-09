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
package com.ntsocial.meshlink.core.takserver.di

import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MeshConfigHandler
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.takserver.TAKMeshIntegration
import com.ntsocial.meshlink.core.takserver.TAKServer
import com.ntsocial.meshlink.core.takserver.TAKServerManager
import com.ntsocial.meshlink.core.takserver.TAKServerManagerImpl
import com.ntsocial.meshlink.core.takserver.fountain.CoTHandler
import com.ntsocial.meshlink.core.takserver.fountain.GenericCoTHandler
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class CoreTakServerModule {
    @Single fun provideTAKServer(dispatchers: CoroutineDispatchers): TAKServer = TAKServer(dispatchers = dispatchers)

    @Single fun provideTAKServerManager(takServer: TAKServer): TAKServerManager = TAKServerManagerImpl(takServer)

    @Single
    fun provideGenericCoTHandler(commandSender: CommandSender, takServerManager: TAKServerManager): CoTHandler =
        GenericCoTHandler(commandSender, takServerManager)

    @Single
    fun provideTAKMeshIntegration(
        takServerManager: TAKServerManager,
        commandSender: CommandSender,
        nodeRepository: NodeRepository,
        serviceRepository: ServiceRepository,
        meshConfigHandler: MeshConfigHandler,
        cotHandler: CoTHandler,
    ): TAKMeshIntegration = TAKMeshIntegration(
        takServerManager,
        commandSender,
        nodeRepository,
        serviceRepository,
        meshConfigHandler,
        cotHandler,
    )
}
