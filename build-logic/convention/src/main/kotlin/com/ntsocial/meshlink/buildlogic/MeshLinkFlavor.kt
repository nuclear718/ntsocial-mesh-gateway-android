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
package com.ntsocial.meshlink.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.ProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    marketplace,
}

@Suppress("EnumEntryName")
enum class MeshLinkFlavor(val dimension: FlavorDimension, val default: Boolean = false) {
    fdroid(FlavorDimension.marketplace),
    google(FlavorDimension.marketplace, default = true),
}

fun configureFlavors(
    commonExtension: CommonExtension,
    flavorConfigurationBlock: ProductFlavor.(flavor: MeshLinkFlavor) -> Unit = {},
) {
    commonExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension -> flavorDimensions += flavorDimension.name }

        when (this) {
            is ApplicationExtension ->
                productFlavors {
                    MeshLinkFlavor.entries.forEach { meshLinkFlavor ->
                        register(meshLinkFlavor.name) {
                            dimension = meshLinkFlavor.dimension.name
                            flavorConfigurationBlock(this, meshLinkFlavor)
                            if (meshLinkFlavor.default) {
                                isDefault = true
                            }
                        }
                    }
                }

            is LibraryExtension ->
                productFlavors {
                    MeshLinkFlavor.entries.forEach { meshLinkFlavor ->
                        register(meshLinkFlavor.name) {
                            dimension = meshLinkFlavor.dimension.name
                            flavorConfigurationBlock(this, meshLinkFlavor)
                            if (meshLinkFlavor.default) {
                                isDefault = true
                            }
                        }
                    }
                }
        }
    }
}
