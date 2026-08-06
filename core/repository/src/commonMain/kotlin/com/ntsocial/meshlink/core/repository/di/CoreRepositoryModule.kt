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
package com.ntsocial.meshlink.core.repository.di

import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.HomoglyphPrefs
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.usecase.SendMessageUseCase
import com.ntsocial.meshlink.core.repository.usecase.SendMessageUseCaseImpl
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Module
class CoreRepositoryModule {
    @Single fun provideChannelOperationLock(): ChannelOperationLock = ChannelOperationLock()

    @Single
    fun provideSendMessageUseCase(
        @Provided nodeRepository: NodeRepository,
        @Provided packetRepository: PacketRepository,
        @Provided radioController: RadioController,
        @Provided homoglyphEncodingPrefs: HomoglyphPrefs,
        @Provided messageQueue: MessageQueue,
        @Provided radioConfigRepository: RadioConfigRepository,
    ): SendMessageUseCase = SendMessageUseCaseImpl(
        nodeRepository,
        packetRepository,
        radioController,
        homoglyphEncodingPrefs,
        messageQueue,
        radioConfigRepository,
    )
}
