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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ntsocial.meshlink.core.database.MeshtasticDatabase.Companion.configureCommon
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Returns a [RoomDatabase.Builder] configured for iOS with the given [dbName]. */
@OptIn(ExperimentalForeignApi::class)
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> {
    val dbFilePath = databaseDirectory() + "/$dbName.db"
    return Room.databaseBuilder<MeshtasticDatabase>(
        name = dbFilePath,
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
        .setDriver(BundledSQLiteDriver())
}

/** Returns a [RoomDatabase.Builder] configured for an in-memory iOS database. */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(factory = { MeshtasticDatabaseConstructor.initialize() })
        .configureCommon()
        .setDriver(BundledSQLiteDriver())

/** Returns the iOS directory where database files are stored. */
actual fun getDatabaseDirectory(): Path = databaseDirectory().toPath()

/** Deletes the database and its Room-associated files on iOS. */
@OptIn(ExperimentalForeignApi::class)
actual fun deleteDatabase(dbName: String) {
    val dir = databaseDirectory()
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db", null)
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db-wal", null)
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db-shm", null)
}

/** Returns the system FileSystem for iOS. */
actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM

/** Creates a durable, app-private iOS DataStore for database-manager preferences. */
@OptIn(ExperimentalForeignApi::class)
actual fun createDatabaseDataStore(name: String): DataStore<Preferences> {
    val dir = applicationSupportDirectory() + "/datastore"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return PreferenceDataStoreFactory.createWithPath { (dir + "/$name.preferences_pb").toPath() }
}

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String {
    val applicationSupportDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(applicationSupportDirectory?.path)
}

@OptIn(ExperimentalForeignApi::class)
private fun databaseDirectory(): String = (applicationSupportDirectory() + "/databases").also { directory ->
    NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
}
