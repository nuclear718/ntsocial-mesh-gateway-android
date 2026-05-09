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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "com.ntsocial.meshlink.buildlogic"

// Configure the build-logic plugins to target JDK 21
// This improves compatibility for developers building the project or consuming its libraries.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

dependencies {
    // This allows the use of the 'libs' type-safe accessor in the Kotlin source of the plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    compileOnly(libs.android.gradleApiPlugin)
    compileOnly(libs.serialization.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.datadog.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.dokka.gradlePlugin)
    compileOnly(libs.firebase.crashlytics.gradlePlugin)
    compileOnly(libs.google.services.gradlePlugin)
    compileOnly(libs.koin.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    implementation(libs.mokkery.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.androidx.room.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.test.retry.gradlePlugin)
    compileOnly(libs.aboutlibraries.gradlePlugin)

    detektPlugins(libs.detekt.formatting)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

spotless {
    ratchetFrom("origin/main")
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file("../config/spotless/.editorconfig").path)
        licenseHeaderFile(rootProject.file("../config/spotless/copyright.kt"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file("../config/spotless/.editorconfig").path)
        licenseHeaderFile(rootProject.file("../config/spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    baseline = file("detekt-baseline.xml")
    source.setFrom(files("src/main/java", "src/main/kotlin"))
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "com.ntsocial.meshlink.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationFlavors") {
            id = "com.ntsocial.meshlink.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLibrary") {
            id = "com.ntsocial.meshlink.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryFlavors") {
            id = "com.ntsocial.meshlink.android.library.flavors"
            implementationClass = "AndroidLibraryFlavorsConventionPlugin"
        }
        register("androidLint") {
            id = "com.ntsocial.meshlink.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "com.ntsocial.meshlink.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "com.ntsocial.meshlink.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("kotlinXSerialization") {
            id = "com.ntsocial.meshlink.kotlinx.serialization"
            implementationClass = "KotlinXSerializationConventionPlugin"
        }
        register("meshlinkAnalytics") {
            id = "com.ntsocial.meshlink.analytics"
            implementationClass = "AnalyticsConventionPlugin"
        }
        register("meshlinkKoin") {
            id = "com.ntsocial.meshlink.koin"
            implementationClass = "KoinConventionPlugin"
        }
        register("meshlinkDetekt") {
            id = "com.ntsocial.meshlink.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("androidRoom") {
            id = "com.ntsocial.meshlink.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }

        register("meshlinkSpotless") {
            id = "com.ntsocial.meshlink.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }

        register("kmpLibrary") {
            id = "com.ntsocial.meshlink.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }

        register("kmpJvmAndroid") {
            id = "com.ntsocial.meshlink.kmp.jvm.android"
            implementationClass = "KmpJvmAndroidConventionPlugin"
        }

        register("kmpLibraryCompose") {
            id = "com.ntsocial.meshlink.kmp.library.compose"
            implementationClass = "KmpLibraryComposeConventionPlugin"
        }

        register("kmpFeature") {
            id = "com.ntsocial.meshlink.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }

        register("dokka") {
            id = "com.ntsocial.meshlink.dokka"
            implementationClass = "DokkaConventionPlugin"
        }

        register("kover") {
            id = "com.ntsocial.meshlink.kover"
            implementationClass = "KoverConventionPlugin"
        }

        register("root") {
            id = "com.ntsocial.meshlink.root"
            implementationClass = "RootConventionPlugin"
        }

        register("publishing") {
            id = "com.ntsocial.meshlink.publishing"
            implementationClass = "PublishingConventionPlugin"
        }

        register("aboutLibraries") {
            id = "com.ntsocial.meshlink.aboutlibraries"
            implementationClass = "AboutLibrariesConventionPlugin"
        }
    }
}
