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
package com.ntsocial.meshlink.feature.node.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.ntsocial.meshlink.core.model.DeviceHardware
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.device
import com.ntsocial.meshlink.core.resources.hardware
import com.ntsocial.meshlink.core.resources.ic_unverified
import com.ntsocial.meshlink.core.resources.img_hw_unknown
import com.ntsocial.meshlink.core.resources.supported
import com.ntsocial.meshlink.core.resources.supported_by_community
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.icon.HardwareModel
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.Verified
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusGreen
import com.ntsocial.meshlink.core.ui.theme.StatusColors.StatusRed
import com.ntsocial.meshlink.feature.node.model.MetricsState
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeviceDetailsSection(state: MetricsState, modifier: Modifier = Modifier) {
    val node = state.node ?: return
    val deviceHardware = state.deviceHardware ?: return

    SectionCard(title = Res.string.device, modifier = modifier) {
        SelectionContainer {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DeviceAvatar(node.colors.second.toLong(), deviceHardware)
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionDivider()

                val deviceText =
                    state.reportedTarget?.let { target -> "${deviceHardware.displayName} ($target)" }
                        ?: deviceHardware.displayName
                ListItem(
                    text = stringResource(Res.string.hardware),
                    leadingIcon = MeshtasticIcons.HardwareModel,
                    supportingText = deviceText,
                    copyable = true,
                    trailingIcon = null,
                )

                SectionDivider()

                SupportStatusItem(deviceHardware.activelySupported)
            }
        }
    }
}

@Composable
private fun DeviceAvatar(bgColor: Long, deviceHardware: DeviceHardware) {
    Box(
        modifier =
        Modifier.size(100.dp)
            .clip(CircleShape)
            .background(color = Color(bgColor).copy(alpha = .5f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DeviceHardwareImage(deviceHardware, Modifier.fillMaxSize())
    }
}

@Composable
private fun SupportStatusItem(isSupported: Boolean) {
    ListItem(
        text =
        if (isSupported) {
            stringResource(Res.string.supported)
        } else {
            stringResource(Res.string.supported_by_community)
        },
        leadingIcon =
        if (isSupported) {
            MeshtasticIcons.Verified
        } else {
            org.jetbrains.compose.resources.vectorResource(
                com.ntsocial.meshlink.core.resources.Res.drawable.ic_unverified,
            )
        },
        leadingIconTint = if (isSupported) colorScheme.StatusGreen else colorScheme.StatusRed,
        trailingIcon = null,
    )
}

@Composable
private fun DeviceHardwareImage(deviceHardware: DeviceHardware, modifier: Modifier = Modifier) {
    val hwImg = deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0) ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(imageUrl).build(),
        contentScale = ContentScale.Inside,
        contentDescription = deviceHardware.displayName,
        placeholder =
        org.jetbrains.compose.resources.painterResource(
            com.ntsocial.meshlink.core.resources.Res.drawable.img_hw_unknown,
        ),
        error =
        org.jetbrains.compose.resources.painterResource(
            com.ntsocial.meshlink.core.resources.Res.drawable.img_hw_unknown,
        ),
        fallback =
        org.jetbrains.compose.resources.painterResource(
            com.ntsocial.meshlink.core.resources.Res.drawable.img_hw_unknown,
        ),
        modifier = modifier.padding(16.dp),
    )
}
