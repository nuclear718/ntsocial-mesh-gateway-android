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
    alias(libs.plugins.meshlink.android.library)
    alias(libs.plugins.meshlink.android.library.compose)
    id("com.ntsocial.meshlink.koin")
}

android {
    namespace = "com.ntsocial.meshlink.feature.widget"
    resourcePrefix = "widget_"

    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.resources)
    implementation(projects.core.repository)

    implementation(libs.compose.multiplatform.ui) // LocalConfiguration, LocalDensity
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.preview)
    implementation(libs.androidx.glance.appwidget.preview)

    implementation(libs.compose.multiplatform.resources)
    implementation(libs.kermit)
    implementation(libs.koin.annotations)
}
