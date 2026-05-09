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
package com.ntsocial.meshlink.core.data.di

import com.ntsocial.meshlink.core.model.util.MeshDataMapper
import com.ntsocial.meshlink.core.model.util.NodeIdLookup
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Module
@ComponentScan("com.ntsocial.meshlink.core.data")
class CoreDataModule {
    @Single fun provideMeshDataMapper(nodeIdLookup: NodeIdLookup): MeshDataMapper = MeshDataMapper(nodeIdLookup)

    @Single fun provideClock(): Clock = Clock.System
}
