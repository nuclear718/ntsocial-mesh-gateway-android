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
package com.ntsocial.meshlink.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException
import org.meshtastic.proto.LocalConfig

/** Serializer for the [LocalConfig] object defined in localonly.proto. */
object LocalConfigSerializer : OkioSerializer<LocalConfig> {
    override val defaultValue: LocalConfig = LocalConfig()

    override suspend fun readFrom(source: BufferedSource): LocalConfig {
        try {
            return LocalConfig.ADAPTER.decode(source)
        } catch (exception: IOException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: LocalConfig, sink: BufferedSink) {
        LocalConfig.ADAPTER.encode(sink, t)
    }
}
