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

plugins {
    alias(libs.plugins.meshlink.kmp.feature)
    alias(libs.plugins.meshlink.kotlinx.serialization)
}

kotlin {
    android {
        namespace = "com.ntsocial.meshlink.feature.firmware"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.navigation3.ui)
            implementation(projects.core.ble)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.prefs)
            implementation(projects.core.proto)
            implementation(projects.core.service)
            implementation(projects.core.resources)
            implementation(projects.core.ui)

            implementation(libs.coil)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network)
            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)
        }

        androidMain.dependencies { implementation(libs.markdown.renderer.android) }

        commonTest.dependencies { implementation(projects.core.testing) }
    }
}
