/*
 * Copyright (c) 2026 Meshtastic LLC
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
package com.ntsocial.meshlink.feature.node.metrics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.clear
import com.ntsocial.meshlink.core.resources.position_log
import com.ntsocial.meshlink.core.ui.icon.Delete
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Refresh
import com.ntsocial.meshlink.core.ui.util.rememberSaveFileLauncher
import org.jetbrains.compose.resources.stringResource

@Composable
fun PositionLogScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val positions = state.positionLogs

    val exportPositionLauncher = rememberSaveFileLauncher { uri -> viewModel.savePositionCSV(uri, positions) }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = null,
        titleRes = Res.string.position_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = positions,
        timeProvider = { it.time.toDouble() },
        onExportCsv = { exportPositionLauncher("position.csv", "text/csv") },
        extraActions = {
            if (positions.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearPosition() }) {
                    Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.clear))
                }
            }
            if (!state.isLocal) {
                IconButton(onClick = { viewModel.requestPosition() }) {
                    Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                }
            }
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(positions) { _, position ->
                    PositionCard(
                        position = position,
                        displayUnits = state.displayUnits,
                        isSelected = position.time.toDouble() == selectedX,
                        onClick = { onCardClick(position.time.toDouble()) },
                    )
                }
            }
        },
    )
}
