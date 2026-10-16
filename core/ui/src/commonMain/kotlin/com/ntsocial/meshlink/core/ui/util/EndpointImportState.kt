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
package com.ntsocial.meshlink.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.model.util.dispatchMeshtasticUri
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.SharedContact

/** Local imports belong to the screen/session that opened them, including the non-root primary UI scope. */
class EndpointImportState
internal constructor(
    private val epoch: Long,
    private val currentSession: () -> RadioSessionState,
) {
    private var active = true
    var channelSet: ChannelSet? by mutableStateOf(null)
        private set

    var sharedContact: SharedContact? by mutableStateOf(null)
        private set

    fun handleUri(uri: CommonUri, onInvalid: () -> Unit) {
        if (!active) return
        val session = currentSession()
        if (session.epoch != epoch || !session.isConfiguredReady) {
            onInvalid()
            return
        }
        uri.dispatchMeshtasticUri(
            onChannel = {
                sharedContact = null
                channelSet = it
            },
            onContact = {
                channelSet = null
                sharedContact = it
            },
            onInvalid = onInvalid,
        )
    }

    fun clearChannelSet() {
        channelSet = null
    }

    fun clearSharedContact() {
        sharedContact = null
    }

    internal fun dispose() {
        active = false
        clearChannelSet()
        clearSharedContact()
    }
}

/** No root ViewModel fallback: the current endpoint supplies both the session and the confirmation's dependencies. */
@Composable
fun rememberEndpointImportState(): EndpointImportState {
    val scope = currentKoinScope()
    val radio = koinInject<RadioInterfaceService>()
    val session by radio.radioSessionState.collectAsStateWithLifecycle()
    val state =
        remember(scope.id, radio, session.epoch) {
            EndpointImportState(session.epoch) { radio.radioSessionState.value }
        }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}
