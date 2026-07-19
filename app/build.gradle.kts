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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.ntsocial.meshlink.buildlogic.VerifyNoCloudRuntimeComponentsTask
import com.ntsocial.meshlink.buildlogic.VerifyNoCloudRuntimeDependenciesTask
import com.ntsocial.meshlink.buildlogic.configProperties
import com.ntsocial.meshlink.buildlogic.resolveVersionInfo
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.util.Properties

val versionInfo = resolveVersionInfo()

val forbiddenCloudRuntimeManifestEntries =
    listOf(
        "com.google.android.gms.permission.AD_ID",
        "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
        "android.permission.ACCESS_ADSERVICES_AD_ID",
        "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
        "android.ext.adservices",
        "AppMeasurement",
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.mlkit",
        "com.google.maps",
        "com.google.android.datatransport",
        "com.datadoghq",
    )

val forbiddenCloudRuntimeGroups =
    listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.mlkit",
        "com.google.maps",
        "com.google.android.libraries.mapsplatform",
        "com.google.android.datatransport",
        "com.datadoghq",
    )

plugins {
    alias(libs.plugins.meshlink.android.application)
    alias(libs.plugins.meshlink.android.application.flavors)
    alias(libs.plugins.meshlink.android.application.compose)
    id("com.ntsocial.meshlink.koin")
    alias(libs.plugins.kotlin.parcelize)
    id("com.ntsocial.meshlink.aboutlibraries")
    id("dev.mokkery")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

configure<ApplicationExtension> {
    namespace = "com.ntsocial.meshlink.app"

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }
    defaultConfig {
        applicationId = configProperties.getProperty("APPLICATION_ID")

        versionCode = versionInfo.versionCode
        versionName = versionInfo.versionName
        buildConfigField("String", "MIN_FW_VERSION", "\"${versionInfo.minFwVersion}\"")
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"${versionInfo.absMinFwVersion}\"")
        // We have to list all translated languages here,
        // because some of our libs have bogus languages that google play
        // doesn't like and we need to strip them (gr)
        val ci = providers.gradleProperty("ci").map { it.toBoolean() }.getOrElse(false)
        if (ci) {
            logger.lifecycle("CI build detected - limiting locale filters for faster packaging")
            androidResources.localeFilters.addAll(listOf("en"))
        } else {
            androidResources.localeFilters.addAll(
                listOf(
                    "en",
                    "ar",
                    "bg",
                    "ca",
                    "cs",
                    "de",
                    "el",
                    "es",
                    "et",
                    "fi",
                    "fr",
                    "ga",
                    "gl",
                    "hr",
                    "ht",
                    "hu",
                    "is",
                    "it",
                    "iw",
                    "ja",
                    "ko",
                    "lt",
                    "nl",
                    "no",
                    "pl",
                    "pt",
                    "pt-rBR",
                    "ro",
                    "ru",
                    "sk",
                    "sl",
                    "sq",
                    "sr",
                    "srp",
                    "sv",
                    "tr",
                    "uk",
                    "zh-rCN",
                    "zh-rTW",
                ),
            )
        }
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Android App Bundles perform their own ABI delivery, and AGP cannot build a bundle while APK ABI splits are
    // enabled. Keep APK splits for F-Droid/IzzyOnDroid, but disable them automatically for every bundle invocation.
    val isBundleInvocation = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    val disableSplits =
        isBundleInvocation ||
            providers.gradleProperty("meshlink.disableAbiSplits").map { it.toBoolean() }.getOrElse(false)

    // Enable ABI splits to generate smaller APKs per architecture for F-Droid/IzzyOnDroid
    splits {
        abi {
            isEnable = !disableSplits
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

    packaging {
        jniLibs {
            // Keep debug symbols in native libraries so reproducible builds don't depend
            // on the exact NDK version used for stripping. This avoids RB failures when
            // IzzyOnDroid/F-Droid rebuilds use a different NDK than our CI.
            // See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
            keepDebugSymbols.add("**/*.so")
        }
    }

    buildTypes {
        release {
            if (keystoreProperties["storeFile"] != null) {
                signingConfig = signingConfigs.named("release").get()
            }
            isDebuggable = false
        }
    }
    bundle { language { enableSplit = false } }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.flavorName?.let { flavor -> variant.applicationId.set("com.ntsocial.meshlink.$flavor.debug") }
    }

    onVariants { variant ->
        if (variant.flavorName == "google" || variant.flavorName == "fdroid") {
            val variantName = variant.name
            val variantNameCapped = variant.name.replaceFirstChar { it.uppercase() }
            val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            val verificationTask =
                tasks.register<VerifyNoCloudRuntimeComponentsTask>(
                    "verify${variantNameCapped}NoCloudRuntimeComponents",
                ) {
                    group = "verification"
                    description =
                        "Fails if the $variantName manifest contains Google cloud, Maps, ML Kit, or diagnostics components."
                    this.variantName.set(variantName)
                    forbiddenEntries.set(forbiddenCloudRuntimeManifestEntries)
                    this.mergedManifest.set(mergedManifest)
                }

            tasks
                .matching { it.name == "assemble$variantNameCapped" || it.name == "bundle$variantNameCapped" }
                .configureEach { finalizedBy(verificationTask) }
        }
    }
}

val verifyGoogleReleaseNoCloudRuntimeDependencies =
    tasks.register<VerifyNoCloudRuntimeDependenciesTask>("verifyGoogleReleaseNoCloudRuntimeDependencies") {
        group = "verification"
        description = "Rejects Google cloud/Maps/ML Kit and Datadog artifacts from the Play release runtime graph."

        val configuration = configurations.getByName("googleReleaseRuntimeClasspath")
        val forbidden =
            configuration.incoming.resolutionResult.allComponents
                .mapNotNull { component ->
                    val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                    val group = id.group
                    val blocked =
                        forbiddenCloudRuntimeGroups.any { prefix ->
                            group == prefix || group.startsWith("$prefix.")
                        }
                    if (blocked) {
                        "${id.group}:${id.module}:${id.version}"
                    } else {
                        null
                    }
                }
                .distinct()
                .sorted()
        forbiddenDependencies.set(forbidden)
    }

tasks
    .matching { it.name == "assembleGoogleRelease" || it.name == "bundleGoogleRelease" }
    .configureEach { dependsOn(verifyGoogleReleaseNoCloudRuntimeDependencies) }

dependencies {
    implementation(projects.core.ble)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.di)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.meshcore)
    implementation(projects.core.navigation)
    implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
    implementation(projects.core.network)
    implementation(projects.core.nfc)
    implementation(projects.core.prefs)
    implementation(projects.core.proto)
    implementation(projects.core.service)
    implementation(projects.core.resources)
    implementation(projects.core.ui)
    implementation(projects.core.barcode)
    implementation(projects.core.takserver)
    implementation(projects.feature.intro)
    implementation(projects.feature.messaging)
    implementation(projects.feature.connections)
    implementation(projects.feature.meshcore)
    implementation(projects.feature.node)
    implementation(projects.feature.settings)
    implementation(projects.feature.firmware)
    implementation(projects.feature.wifiProvision)
    implementation(projects.feature.widget)

    implementation(libs.jetbrains.compose.material3.adaptive)
    implementation(libs.jetbrains.compose.material3.adaptive.layout)
    implementation(libs.jetbrains.compose.material3.adaptive.navigation)
    implementation(libs.material)
    implementation(libs.compose.multiplatform.animation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.ui.tooling.preview)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.coil)
    implementation(libs.coil.network.ktor3)
    implementation(libs.coil.svg)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.usb.serial.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.annotations)
    implementation(libs.accompanist.permissions)
    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.glance.preview)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.koin.test)
    // Robolectric runs Android code on the host JVM. This provides sqliteJni.dll on Windows.
    testRuntimeOnly("androidx.sqlite:sqlite-bundled-jvm:2.6.2")
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.multiplatform.ui.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.glance.appwidget)
}
