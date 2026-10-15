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
package com.ntsocial.meshlink.core.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.parameter.ParametersHolder
import org.koin.core.scope.Scope
import kotlin.reflect.KClass

/** Explicit constructors bound to one runtime. Missing entries never resolve a root ViewModel definition. */
class EndpointViewModelFactory(private val scope: Scope) {
    val scopeId: String = scope.id
    private val models = mutableSetOf<ViewModel>()
    private var closed = false
    private val constructors =
        mutableMapOf<KClass<out ViewModel>, Scope.(SavedStateHandle, ParametersHolder) -> ViewModel>()

    fun <T : ViewModel> register(type: KClass<T>, constructor: Scope.(SavedStateHandle, ParametersHolder) -> T) {
        check(type !in constructors) { "Duplicate endpoint ViewModel: $type" }
        constructors[type] = constructor
    }

    fun create(type: KClass<out ViewModel>, state: SavedStateHandle, parameters: ParametersHolder): ViewModel {
        check(!closed && scope.isNotClosed()) { "Endpoint runtime is closed" }
        val constructor = checkNotNull(constructors[type]) { "Missing endpoint ViewModel factory: $type" }
        return constructor(scope, state, parameters).also { model ->
            models.add(model)
            model.addCloseable(
                object : AutoCloseable {
                    override fun close() {
                        models.remove(model)
                    }
                },
            )
        }
    }

    /** Navigation may still hold an outgoing entry; revoke its asynchronous work before closing its repositories. */
    suspend fun close() = withContext(Dispatchers.Main.immediate) {
        closed = true
        val current = models.toList()
        ViewModelStore().apply {
            current.forEachIndexed { index, model -> put(index.toString(), model) }
            clear()
        }
    }
}
