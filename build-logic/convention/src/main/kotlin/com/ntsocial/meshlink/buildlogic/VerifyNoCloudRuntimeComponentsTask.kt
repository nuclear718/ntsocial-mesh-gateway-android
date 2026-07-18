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

package com.ntsocial.meshlink.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification task has no outputs")
abstract class VerifyNoCloudRuntimeComponentsTask : DefaultTask() {

    @get:Input abstract val variantName: Property<String>

    @get:Input abstract val forbiddenEntries: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verifyManifest() {
        val manifestText = mergedManifest.get().asFile.readText()
        val detectedEntries = forbiddenEntries.get().filter(manifestText::contains)
        if (detectedEntries.isNotEmpty()) {
            throw GradleException(
                "Forbidden cloud runtime components found in ${variantName.get()}: ${detectedEntries.joinToString()}",
            )
        }
    }
}
