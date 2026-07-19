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
package com.ntsocial.meshlink.feature.settings.radio.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.common.util.MetricFormatter
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.close
import com.ntsocial.meshlink.core.resources.delivery_confirmed
import com.ntsocial.meshlink.core.resources.delivery_confirmed_reboot_warning
import com.ntsocial.meshlink.core.resources.error
import com.ntsocial.meshlink.core.ui.component.MeshtasticDialog
import com.ntsocial.meshlink.core.ui.icon.Error
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Success
import com.ntsocial.meshlink.feature.settings.radio.ResponseState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val AUTO_DISMISS_DELAY_MS = 1500L

@Composable
fun <T> PacketResponseStateDialog(
    state: ResponseState<T>,
    onDismiss: () -> Unit = {},
    onComplete: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    LaunchedEffect(state) {
        if (state is ResponseState.Success) {
            delay(AUTO_DISMISS_DELAY_MS)
            onDismiss()
            onBack()
        }
    }

    MeshtasticDialog(
        onDismiss = if (state is ResponseState.Loading) onDismiss else null,
        title = null,
        icon = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (state) {
                    is ResponseState.Loading -> {
                        LoadingContent(state = state, onComplete = onComplete)
                    }

                    is ResponseState.Success -> {
                        SuccessContent()
                    }

                    is ResponseState.Error -> {
                        ErrorContent(state = state)
                    }

                    ResponseState.Empty -> {}
                }
            }
        },
        dismissable = false,
        onConfirm =
        if (state !is ResponseState.Loading) {
            {
                onDismiss()
                onBack()
            }
        } else {
            null
        },
        confirmText = stringResource(Res.string.close),
        dismissText = if (state is ResponseState.Loading) stringResource(Res.string.cancel) else null,
    )
}

@Composable
@Suppress("MagicNumber")
private fun LoadingContent(state: ResponseState.Loading, onComplete: () -> Unit) {
    val clampedProgress = (state.completed.toFloat() / state.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(targetValue = clampedProgress, label = "progress")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = MetricFormatter.percent(progress * 100f, decimalPlaces = 0),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        state.status?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (state.completed >= state.total) onComplete()
}

@Composable
private fun SuccessContent() {
    Icon(
        imageVector = MeshtasticIcons.Success,
        contentDescription = null,
        modifier = Modifier.size(84.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.delivery_confirmed),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.delivery_confirmed_reboot_warning),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(state: ResponseState.Error) {
    Icon(
        imageVector = MeshtasticIcons.Error,
        contentDescription = null,
        modifier = Modifier.size(84.dp),
        tint = MaterialTheme.colorScheme.error,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.error),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "${state.error.asString()}.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
