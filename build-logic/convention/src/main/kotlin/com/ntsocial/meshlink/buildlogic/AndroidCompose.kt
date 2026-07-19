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
package com.ntsocial.meshlink.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Configure Compose-specific options */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.apply { buildFeatures.compose = true }

    // CMP is the sole Compose version authority (BOM removed from the catalog).
    // Some third-party Compose libraries carry a transitive compose-bom whose constraints conflict with
    // CMP-published AndroidX artifacts.
    // Exclude it globally so CMP's own dependency graph wins.
    configurations.configureEach { exclude(mapOf("group" to "androidx.compose", "module" to "compose-bom")) }

    // CMP publishes these core AndroidX groups at an AndroidX version tag that
    // tracks (but does not equal) the CMP version. The exact mapping lives in
    // the CMP release notes; we mirror it via the `androidx-compose-bom-aligned`
    // version ref in libs.versions.toml. Material, Material3, and Adaptive follow
    // separate AndroidX version numbers and must NOT be included here.
    val androidxComposeVersion = libs.version("androidx-compose-bom-aligned")
    val cmpAlignedGroups =
        setOf(
            "androidx.compose.animation",
            "androidx.compose.foundation",
            "androidx.compose.runtime",
            "androidx.compose.ui",
        )
    // Keep CMP-aligned AndroidX artifacts on the version supplied by this project's Compose Multiplatform release.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group in cmpAlignedGroups) {
                useVersion(androidxComposeVersion)
            }
        }
    }

    val hasAndroidTest = project.projectDir.resolve("src/androidTest").exists()
    dependencies {
        "debugImplementation"(libs.library("compose-multiplatform-ui-tooling"))
        "implementation"(libs.library("compose-multiplatform-runtime"))
        "runtimeOnly"(libs.library("androidx-compose-runtime-tracing"))

        "implementation"(libs.library("compose-multiplatform-resources"))

        // Add Espresso explicitly to avoid version mismatch issues on newer Android versions
        if (hasAndroidTest) {
            "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
        }
    }
    configureComposeCompiler()
}
