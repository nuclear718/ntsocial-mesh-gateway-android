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

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.meshlink.kmp.library)
    alias(libs.plugins.meshlink.kmp.library.compose)
    alias(libs.plugins.meshlink.kotlinx.serialization)
    id("com.ntsocial.meshlink.kmp.jvm.android")
}

kotlin {
    android {
        namespace = "com.ntsocial.meshlink.ios.runtime"
        androidResources.enable = false
    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { target -> target.name == "iosArm64" || target.name == "iosSimulatorArm64" }
        .configureEach {
            binaries.framework {
                baseName = "MeshLinkKit"
                isStatic = true
                binaryOption("bundleId", "com.ntsocial.meshlink.ios.framework")
            }
        }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ble)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.domain)
            implementation(projects.core.gateway)
            implementation(projects.core.meshcore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.prefs)
            implementation(projects.core.radioFleet)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.takserver)
            implementation(projects.core.ui)

            implementation(projects.feature.connections)
            implementation(projects.feature.intro)
            implementation(projects.feature.meshcore)
            implementation(projects.feature.messaging)
            implementation(projects.feature.node)
            implementation(projects.feature.settings)
            implementation(projects.feature.wifiProvision)

            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.kermit)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.okio)
        }
        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.ntsocial.meshlink.ios.runtime.resources"
}
