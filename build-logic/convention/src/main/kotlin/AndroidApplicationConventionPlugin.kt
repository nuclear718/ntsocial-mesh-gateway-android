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
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import com.ntsocial.meshlink.buildlogic.configureGraphTasks
import com.ntsocial.meshlink.buildlogic.configureKotlinAndroid
import com.ntsocial.meshlink.buildlogic.configureTestOptions

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "org.gradle.test-retry")
            apply(plugin = "com.ntsocial.meshlink.android.lint")
            apply(plugin = "com.ntsocial.meshlink.detekt")
            apply(plugin = "com.ntsocial.meshlink.spotless")
            apply(plugin = "com.ntsocial.meshlink.analytics")
            apply(plugin = "com.ntsocial.meshlink.kover")
            apply(plugin = "com.ntsocial.meshlink.dokka")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig { vectorDrawables.useSupportLibrary = true }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            rootProject.file("config/proguard/shared-rules.pro"),
                            "proguard-rules.pro",
                        )
                    }
                    getByName("debug") {
                        isDebuggable = true
                        isPseudoLocalesEnabled = true
                        enableAndroidTestCoverage = true
                        // Disable PNG crunching for faster debug builds
                        isCrunchPngs = false
                    }
                }

                buildFeatures { buildConfig = true }
            }
            configureTestOptions()
            configureGraphTasks()
        }
    }
}
