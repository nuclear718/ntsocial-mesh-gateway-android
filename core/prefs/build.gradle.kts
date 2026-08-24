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
    alias(libs.plugins.meshlink.kmp.library)
    id("com.ntsocial.meshlink.koin")
}

kotlin {
    android {
        namespace = "com.ntsocial.meshlink.core.prefs"
        androidResources.enable = false
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.repository)
            implementation(projects.core.radioFleet)
            implementation(projects.core.common)
            implementation(projects.core.di)

            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }

        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}
