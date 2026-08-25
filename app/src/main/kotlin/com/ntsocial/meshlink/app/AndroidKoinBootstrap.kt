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
package com.ntsocial.meshlink.app

import android.app.Application
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.app.di.AndroidKoinApp
import com.ntsocial.meshlink.app.radio.radioEndpointKoinModule
import com.ntsocial.meshlink.core.common.ContextServices
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin

/** Process-wide Android DI bootstrap shared by early ContentProvider startup and [MeshUtilApplication]. */
internal object AndroidKoinBootstrap {
    @Volatile private var started = false

    fun ensureStarted(application: Application) {
        if (started) return

        synchronized(this) {
            if (started) return

            ContextServices.app = application
            startKoin<AndroidKoinApp> {
                androidContext(application)
                workManagerFactory()
            }
            loadKoinModules(radioEndpointKoinModule)
            started = true
            Logger.i { "Android Koin bootstrap complete" }
        }
    }

    fun stop() {
        synchronized(this) {
            if (!started) return
            stopKoin()
            started = false
        }
    }
}
