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
package com.ntsocial.meshlink.feature.intro

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.communicate_off_the_grid
import com.ntsocial.meshlink.core.resources.create_your_own_networks
import com.ntsocial.meshlink.core.resources.easily_set_up_private_mesh_networks
import com.ntsocial.meshlink.core.resources.get_started
import com.ntsocial.meshlink.core.resources.img_ntsocial_butterfly_logo
import com.ntsocial.meshlink.core.resources.intro_welcome
import com.ntsocial.meshlink.core.resources.meshtastic_app_name
import com.ntsocial.meshlink.core.resources.share_your_location_in_real_time
import com.ntsocial.meshlink.core.resources.stay_connected_anywhere
import com.ntsocial.meshlink.core.resources.track_and_share_locations
import com.ntsocial.meshlink.core.ui.icon.Antenna
import com.ntsocial.meshlink.core.ui.icon.MeshHub
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.NearMe
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The initial welcome screen for the app introduction flow. It displays a brief overview of the app's key features.
 *
 * @param onGetStarted Callback invoked when the user proceeds from the welcome screen.
 */
@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit) {
    val features =
        listOf(
            FeatureUIData(
                icon = MeshtasticIcons.Antenna,
                titleRes = Res.string.stay_connected_anywhere,
                subtitleRes = Res.string.communicate_off_the_grid,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.MeshHub,
                titleRes = Res.string.create_your_own_networks,
                subtitleRes = Res.string.easily_set_up_private_mesh_networks,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.NearMe,
                titleRes = Res.string.track_and_share_locations,
                subtitleRes = Res.string.share_your_location_in_real_time,
            ),
        )

    NtsocialIntroScaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = {}, // No skip on welcome
                onConfigure = onGetStarted,
                skipButtonText = "", // Not shown
                configureButtonText = stringResource(Res.string.get_started),
                showSkipButton = false, // Explicitly hide skip for welcome
            )
        },
    ) {
        Column(
            modifier = ntsocialIntroPanelModifier().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            Image(
                painter = painterResource(Res.drawable.img_ntsocial_butterfly_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = stringResource(Res.string.intro_welcome),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Start,
            )
            Text(
                text = stringResource(Res.string.meshtastic_app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Start,
            )
            features.forEach { feature -> FeatureRow(feature = feature) }
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    WelcomeScreen(onGetStarted = {})
}
