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
    alias(libs.plugins.kotlin.parcelize)
    id("com.ntsocial.meshlink.kmp.jvm.android")
    id("com.ntsocial.meshlink.koin")
}

kotlin {
    jvm()

    android {
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.okio)
            api(libs.uri.kmp)
            implementation(libs.kermit)
        }
        androidMain.dependencies { api(libs.androidx.core.ktx) }

        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}
