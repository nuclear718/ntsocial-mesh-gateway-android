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
package com.ntsocial.meshlink.feature.settings.component

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.common.util.toDate
import com.ntsocial.meshlink.core.common.util.toInstant
import com.ntsocial.meshlink.core.database.DatabaseConstants
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.app_settings
import com.ntsocial.meshlink.core.resources.device_db_cache_limit
import com.ntsocial.meshlink.core.resources.device_db_cache_limit_summary
import com.ntsocial.meshlink.core.resources.export_data_csv
import com.ntsocial.meshlink.core.resources.save_rangetest
import com.ntsocial.meshlink.core.ui.component.DropDownPreference
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Output
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Locale

/** Section for settings related to data persistence and exports. */
@Composable
fun PersistenceSection(
    cacheLimit: Int,
    onSetCacheLimit: (Int) -> Unit,
    nodeShortName: String,
    onExportData: (android.net.Uri) -> Unit,
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(nowMillis.toInstant().toDate())

    val exportRangeTestLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.data?.let { uri -> onExportData(uri) }
            }
        }

    val exportDataLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.data?.let { uri -> onExportData(uri) }
            }
        }

    ExpressiveSection(title = stringResource(Res.string.app_settings)) {
        val cacheItems = remember {
            (DatabaseConstants.MIN_CACHE_LIMIT..DatabaseConstants.MAX_CACHE_LIMIT).map { it.toLong() to it.toString() }
        }
        DropDownPreference(
            title = stringResource(Res.string.device_db_cache_limit),
            enabled = true,
            items = cacheItems,
            selectedItem = cacheLimit.toLong(),
            onItemSelected = { selected -> onSetCacheLimit(selected.toInt()) },
            summary = stringResource(Res.string.device_db_cache_limit_summary),
        )

        ListItem(
            text = stringResource(Res.string.save_rangetest),
            leadingIcon = MeshtasticIcons.Output,
            trailingIcon = null,
        ) {
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, "Meshtastic_rangetest_${nodeShortName}_$timestamp.csv")
                }
            exportRangeTestLauncher.launch(intent)
        }

        ListItem(
            text = stringResource(Res.string.export_data_csv),
            leadingIcon = MeshtasticIcons.Output,
            trailingIcon = null,
        ) {
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, "Meshtastic_datalog_${nodeShortName}_$timestamp.csv")
                }
            exportDataLauncher.launch(intent)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PersistenceSectionPreview() {
    AppTheme { PersistenceSection(cacheLimit = 100, onSetCacheLimit = {}, nodeShortName = "TEST", onExportData = {}) }
}
