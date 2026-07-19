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

import org.gradle.api.Project

/**
 * Shared version metadata resolved from config.properties, environment variables, Gradle properties, and a legacy
 * git commit-count fallback. Used by both the Android app and Desktop host shells to avoid duplicating resolution
 * logic.
 */
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val minFwVersion: String,
    val absMinFwVersion: String,
)

/**
 * Resolves version information using the following precedence:
 * 1. Gradle properties injected by the IDE or CI (`android.injected.version.code`, etc.)
 * 2. Environment variables (`VERSION_CODE`, `VERSION_NAME`)
 * 3. Explicit `VERSION_CODE` / `VERSION_NAME` values in `config.properties`
 * 4. Git commit count + offset for version code when `VERSION_CODE` is intentionally absent
 * 5. Fallback defaults
 */
fun Project.resolveVersionInfo(): VersionInfo {
    val gitVersionProvider = providers.of(GitVersionValueSource::class.java) {}
    val vcOffset = configProperties.getProperty("VERSION_CODE_OFFSET")?.toInt() ?: 0
    val configuredVersionCode = configProperties.getProperty("VERSION_CODE")?.toInt()

    val versionCode =
        findProperty("android.injected.version.code")?.toString()?.toInt()
            ?: System.getenv("VERSION_CODE")?.toInt()
            ?: configuredVersionCode
            ?: (gitVersionProvider.get().toInt() + vcOffset)

    val versionName =
        findProperty("android.injected.version.name")?.toString()
            ?: findProperty("appVersionName")?.toString()
            ?: System.getenv("VERSION_NAME")
            ?: configProperties.getProperty("VERSION_NAME")
            ?: "1.0.0"

    val minFwVersion = configProperties.getProperty("MIN_FW_VERSION") ?: ""
    val absMinFwVersion = configProperties.getProperty("ABS_MIN_FW_VERSION") ?: ""

    return VersionInfo(
        versionCode = versionCode,
        versionName = versionName,
        minFwVersion = minFwVersion,
        absMinFwVersion = absMinFwVersion,
    )
}
