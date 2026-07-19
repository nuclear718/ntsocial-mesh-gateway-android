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
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import com.ntsocial.meshlink.buildlogic.configProperties

class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            group = "com.ntsocial.meshlink"

            if (version == "unspecified") {
                version =
                        providers.environmentVariable("VERSION").orNull
                        ?: providers.environmentVariable("VERSION_NAME").orNull
                        ?: configProperties.getProperty("VERSION_NAME")
                        ?: "0.0.0-SNAPSHOT"
            }

            val githubActor = providers.environmentVariable("GITHUB_ACTOR")
            val githubToken = providers.environmentVariable("GITHUB_TOKEN")

            if (githubActor.isPresent && githubToken.isPresent) {
                extensions.configure<PublishingExtension> {
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            val githubRepository =
                                providers.environmentVariable("GITHUB_REPOSITORY").orElse("NTsocial/ntsocial-meshlink-android")
                            url = uri("https://maven.pkg.github.com/${githubRepository.get()}")
                            credentials {
                                username = githubActor.get()
                                password = githubToken.get()
                            }
                        }
                    }
                }
            }
        }
    }
}
