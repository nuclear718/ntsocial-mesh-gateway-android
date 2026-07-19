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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import com.ntsocial.meshlink.buildlogic.configureAndroidMarketplaceFallback
import com.ntsocial.meshlink.buildlogic.configureGraphTasks
import com.ntsocial.meshlink.buildlogic.configureKmpTestDependencies
import com.ntsocial.meshlink.buildlogic.configureKotlinMultiplatform
import com.ntsocial.meshlink.buildlogic.configureTestOptions
import com.ntsocial.meshlink.buildlogic.isDesktopOnly
import com.ntsocial.meshlink.buildlogic.libs
import com.ntsocial.meshlink.buildlogic.plugin

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugin("kotlin-multiplatform").get().pluginId)
            if (!isDesktopOnly) {
                apply(plugin = libs.plugin("android-kotlin-multiplatform-library").get().pluginId)
                apply(plugin = "com.ntsocial.meshlink.android.lint")
            }
            apply(plugin = "com.ntsocial.meshlink.detekt")
            apply(plugin = "com.ntsocial.meshlink.spotless")
            apply(plugin = "com.ntsocial.meshlink.dokka")
            apply(plugin = "com.ntsocial.meshlink.kover")
            apply(plugin = "org.gradle.test-retry")
            apply(plugin = libs.plugin("mokkery").get().pluginId)

            configureKotlinMultiplatform()
            configureKmpTestDependencies()
            configureTestOptions()
            configureGraphTasks()
            if (!isDesktopOnly) {
                configureAndroidMarketplaceFallback()
            }
        }
    }
}
