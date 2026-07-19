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
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.meshlink.android.library)
    alias(libs.plugins.meshlink.android.library.compose)
    alias(libs.plugins.meshlink.android.library.flavors)
}

configure<LibraryExtension> {
    namespace = "com.ntsocial.meshlink.core.barcode"

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(projects.core.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.accompanist.permissions)
    implementation(libs.kermit)

    // Decode locally in both flavors. ZXing does not require Google Play services or a network connection.
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.viewfinder.compose)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.multiplatform.ui.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
