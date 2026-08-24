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

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.compose.currentKoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.ParametersDefinition

/**
 * Resolves a ViewModel with a key that includes the current Koin scope.
 *
 * Navigation 3 keeps a route's ViewModelStore while Android users switch radio tabs. A scope-qualified key prevents a
 * ViewModel created for one radio from being reused for another radio that has the same navigation route.
 */
@Composable
inline fun <reified T : ViewModel> scopedViewModel(
    key: String? = null,
    noinline parameters: ParametersDefinition? = null,
): T {
    val scope = currentKoinScope()
    val typeKey = T::class.qualifiedName ?: T::class.simpleName ?: "viewmodel"
    return koinViewModel(key = "${scope.id}:${key ?: typeKey}", scope = scope, parameters = parameters)
}
