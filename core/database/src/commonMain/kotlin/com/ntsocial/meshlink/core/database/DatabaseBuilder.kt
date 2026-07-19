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
package com.ntsocial.meshlink.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.RoomDatabase
import okio.FileSystem
import okio.Path

/** Returns a [RoomDatabase.Builder] configured for the current platform with the given [dbName]. */
expect fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase>

/** Returns a [RoomDatabase.Builder] configured for an in-memory database on the current platform. */
expect fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase>

/** Returns the platform-specific directory where database files are stored. */
expect fun getDatabaseDirectory(): Path

/** Deletes the database with the given [dbName] and its associated files (e.g., -wal, -shm). */
expect fun deleteDatabase(dbName: String)

/** Returns the [FileSystem] to use for database file operations. */
expect fun getFileSystem(): FileSystem

/** Creates a platform-specific [DataStore] for database-related preferences. */
expect fun createDatabaseDataStore(name: String): DataStore<Preferences>
