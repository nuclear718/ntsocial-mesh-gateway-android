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

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class GitVersionValueSource : ValueSource<String, GitVersionValueSource.Params> {
    interface Params : ValueSourceParameters

    @get:Inject abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        return try {
            execOperations.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                standardOutput = output
            }
            output.toString().trim()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw IllegalStateException(
                "Failed to determine git commit count for versionCode. " +
                    "Ensure you have a full git history (not a shallow clone) and .git is present.",
                e,
            )
        }
    }
}
