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
package com.ntsocial.meshlink.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.okay
import com.ntsocial.meshlink.core.resources.traceroute
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusGreen
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusOrange
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusYellow
import com.ntsocial.meshlink.core.ui.util.annotateTraceroute
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel

/**
 * Handles the display of the traceroute alert when a response is received. Consolidates the side effect logic from the
 * main application screens into common code.
 */
@Composable
fun TracerouteAlertHandler(uiViewModel: UIViewModel) {
    val traceRouteResponse by uiViewModel.tracerouteResponse.collectAsStateWithLifecycle(null)
    var dismissedTracerouteRequestId by remember { mutableStateOf<Int?>(null) }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(traceRouteResponse, dismissedTracerouteRequestId) {
        val response = traceRouteResponse
        if (response != null && response.requestId != dismissedTracerouteRequestId) {
            uiViewModel.showAlert(
                titleRes = Res.string.traceroute,
                composableMessage = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text =
                            annotateTraceroute(
                                response.message,
                                statusGreen = colorScheme.StatusGreen,
                                statusYellow = colorScheme.StatusYellow,
                                statusOrange = colorScheme.StatusOrange,
                            ),
                        )
                    }
                },
                confirmTextRes = Res.string.okay,
                onConfirm = {
                    dismissedTracerouteRequestId = response.requestId
                    uiViewModel.clearTracerouteResponse()
                },
                onDismiss = {
                    uiViewModel.clearTracerouteResponse()
                    dismissedTracerouteRequestId = null
                },
            )
        }
    }
}
