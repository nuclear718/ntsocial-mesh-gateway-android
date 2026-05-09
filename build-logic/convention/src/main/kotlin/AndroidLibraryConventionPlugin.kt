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
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import com.ntsocial.meshlink.buildlogic.configureGraphTasks
import com.ntsocial.meshlink.buildlogic.configureKotlinAndroid
import com.ntsocial.meshlink.buildlogic.configureTestOptions
import com.ntsocial.meshlink.buildlogic.disableUnnecessaryAndroidTests

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "org.gradle.test-retry")
            apply(plugin = "com.ntsocial.meshlink.android.lint")
            apply(plugin = "com.ntsocial.meshlink.detekt")
            apply(plugin = "com.ntsocial.meshlink.spotless")
            apply(plugin = "com.ntsocial.meshlink.dokka")
            apply(plugin = "com.ntsocial.meshlink.kover")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    // When flavorless modules depend on flavored modules (like :core:data),
                    // they need a strategy to pick a variant. We default to 'google'.
                    missingDimensionStrategy("marketplace", "google")
                }

                buildTypes { getByName("debug") { enableAndroidTestCoverage = true } }
            }
            extensions.configure<LibraryAndroidComponentsExtension> { disableUnnecessaryAndroidTests(target) }
            configureTestOptions()
            configureGraphTasks()
        }
    }
}
