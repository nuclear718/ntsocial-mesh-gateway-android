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
package com.ntsocial.meshlink.ios.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.gateway.apple.AppleGatewayContract
import com.ntsocial.meshlink.ios.runtime.resources.ios_app_group_container
import com.ntsocial.meshlink.ios.runtime.resources.ios_available
import com.ntsocial.meshlink.ios.runtime.resources.ios_bluetooth_background
import com.ntsocial.meshlink.ios.runtime.resources.ios_bluetooth_privacy
import com.ntsocial.meshlink.ios.runtime.resources.ios_bundle_identifier
import com.ntsocial.meshlink.ios.runtime.resources.ios_check_count
import com.ntsocial.meshlink.ios.runtime.resources.ios_configured
import com.ntsocial.meshlink.ios.runtime.resources.ios_handoff_accepted
import com.ntsocial.meshlink.ios.runtime.resources.ios_handoff_none
import com.ntsocial.meshlink.ios.runtime.resources.ios_handoff_rejected
import com.ntsocial.meshlink.ios.runtime.resources.ios_integration_heading
import com.ntsocial.meshlink.ios.runtime.resources.ios_integration_summary
import com.ntsocial.meshlink.ios.runtime.resources.ios_missing
import com.ntsocial.meshlink.ios.runtime.resources.ios_not_checked
import com.ntsocial.meshlink.ios.runtime.resources.ios_run_host_check
import com.ntsocial.meshlink.ios.runtime.resources.ios_unavailable
import com.ntsocial.meshlink.ios.runtime.resources.ios_url_scheme
import org.jetbrains.compose.resources.stringResource
import com.ntsocial.meshlink.ios.runtime.resources.Res as IosRes

@Composable
internal fun IntegrationStatusScreen(state: IosShellState, onRefresh: () -> Unit) {
    ShellScreenColumn {
        Text(
            text = stringResource(IosRes.string.ios_integration_heading),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(IosRes.string.ios_integration_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IntegrationReadinessCard(state.hostReadiness)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(IosRes.string.ios_run_host_check))
        }
        Text(
            text = stringResource(IosRes.string.ios_check_count, state.integrationChecks),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ParentHandoffNotice(state.parentHandoffState)
    }
}

/** Compact Apple companion/Gateway diagnostics embedded in the full iOS Settings hierarchy. */
@Composable
internal fun AppleIntegrationSettingsSection(state: IosShellState, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(IosRes.string.ios_integration_heading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(IosRes.string.ios_integration_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IntegrationReadinessCard(state.hostReadiness)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(IosRes.string.ios_run_host_check))
        }
        ParentHandoffNotice(state.parentHandoffState)
    }
}

@Composable
private fun IntegrationReadinessCard(readiness: AppleHostReadiness?) {
    ElevatedCard(colors = shellCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            StatusRow(
                label = stringResource(IosRes.string.ios_bundle_identifier),
                value = readiness?.bundleIdentifier ?: stringResource(IosRes.string.ios_not_checked),
                positive = readiness?.bundleIdentifier?.let { it == AppleGatewayContract.COMPANION_BUNDLE_IDENTIFIER },
            )
            HorizontalDivider()
            BooleanStatusRow(
                label = stringResource(IosRes.string.ios_app_group_container),
                value = readiness?.appGroupContainerAvailable,
                positiveLabel = stringResource(IosRes.string.ios_available),
                negativeLabel = stringResource(IosRes.string.ios_unavailable),
            )
            HorizontalDivider()
            BooleanStatusRow(
                label = stringResource(IosRes.string.ios_bluetooth_privacy),
                value = readiness?.bluetoothPrivacyDescriptionConfigured,
            )
            HorizontalDivider()
            BooleanStatusRow(
                label = stringResource(IosRes.string.ios_bluetooth_background),
                value = readiness?.bluetoothCentralBackgroundModeConfigured,
            )
            HorizontalDivider()
            BooleanStatusRow(
                label = stringResource(IosRes.string.ios_url_scheme),
                value = readiness?.companionUrlSchemeConfigured,
            )
        }
    }
}

@Composable
private fun ParentHandoffNotice(state: ParentHandoffState) {
    StatusNotice(
        text =
        stringResource(
            when (state) {
                ParentHandoffState.NONE -> IosRes.string.ios_handoff_none
                ParentHandoffState.ACCEPTED -> IosRes.string.ios_handoff_accepted
                ParentHandoffState.REJECTED -> IosRes.string.ios_handoff_rejected
            },
        ),
        positive = state == ParentHandoffState.ACCEPTED,
    )
}

@Composable
internal fun ShellScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun BooleanStatusRow(
    label: String,
    value: Boolean?,
    positiveLabel: String = stringResource(IosRes.string.ios_configured),
    negativeLabel: String = stringResource(IosRes.string.ios_missing),
) {
    StatusRow(
        label = label,
        value =
        when (value) {
            true -> positiveLabel
            false -> negativeLabel
            null -> stringResource(IosRes.string.ios_not_checked)
        },
        positive = value,
    )
}

@Composable
internal fun StatusRow(label: String, value: String, positive: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(positive)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StatusNotice(text: String, positive: Boolean) {
    ElevatedCard(colors = shellCardColors(), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(positive)
            Text(text = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun StatusPill(label: String, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        color =
        if (positive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor =
        if (positive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(positive)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusDot(positive: Boolean?) {
    val color =
        when (positive) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.outline
        }
    Spacer(modifier = Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
internal fun shellCardColors() =
    CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA))
