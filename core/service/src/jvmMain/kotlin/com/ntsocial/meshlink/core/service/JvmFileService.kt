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
package com.ntsocial.meshlink.core.service

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.repository.FileService
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import org.koin.core.annotation.Single
import java.io.File

@Single
class JvmFileService(private val dispatchers: CoroutineDispatchers) : FileService {
    override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean =
        withContext(dispatchers.io) {
            try {
                // Treat URI string as a local file path
                val file = File(uri.toString())
                file.parentFile?.mkdirs()
                file.sink().buffer().use { sink -> block(sink) }
                true
            } catch (e: Exception) {
                Logger.e(e) { "Failed to write to URI: $uri" }
                false
            }
        }

    override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean =
        withContext(dispatchers.io) {
            try {
                val file = File(uri.toString())
                file.source().buffer().use { source -> block(source) }
                true
            } catch (e: Exception) {
                Logger.e(e) { "Failed to read from URI: $uri" }
                false
            }
        }
}
