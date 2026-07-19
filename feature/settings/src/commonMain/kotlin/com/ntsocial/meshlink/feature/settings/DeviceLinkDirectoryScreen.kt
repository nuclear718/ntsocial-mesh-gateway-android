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
package com.ntsocial.meshlink.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.device_links
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.component.MainAppBar
import com.ntsocial.meshlink.core.ui.icon.Language
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import org.jetbrains.compose.resources.stringResource

/** Directory of every imported msh.to short code. Tapping a row opens `msh.to/{shortCode}` in the browser. */
@Composable
fun DeviceLinkDirectoryScreen(
    viewModel: DeviceLinkDirectoryViewModel,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val links by viewModel.links.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.device_links),
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            items(links, key = { it.shortCode }) { link ->
                ListItem(
                    text = link.description ?: link.shortCode,
                    supportingText = "msh.to/${link.shortCode}",
                    trailingIcon = MeshtasticIcons.Language,
                    onClick = { uriHandler.openUri(link.url) },
                )
            }
        }
    }
}
