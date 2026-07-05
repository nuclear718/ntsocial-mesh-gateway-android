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
package com.ntsocial.meshlink.feature.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.configure_critical_alerts
import com.ntsocial.meshlink.core.resources.critical_alerts
import com.ntsocial.meshlink.core.resources.critical_alerts_dnd_request_text
import com.ntsocial.meshlink.core.resources.skip
import org.jetbrains.compose.resources.stringResource

/**
 * Screen for explaining and guiding the user to configure critical alert settings. This screen is part of the app
 * introduction flow and appears after the general notification permission screen.
 *
 * @param onSkip Callback invoked if the user chooses to skip configuring critical alerts.
 * @param onConfigure Callback invoked when the user proceeds to configure critical alerts.
 */
@Composable
internal fun CriticalAlertsScreen(onSkip: () -> Unit, onConfigure: () -> Unit) {
    NtsocialIntroScaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = onSkip,
                onConfigure = onConfigure,
                configureButtonText = stringResource(Res.string.configure_critical_alerts),
                skipButtonText = stringResource(Res.string.skip),
            )
        },
    ) {
        Column(
            modifier = ntsocialIntroPanelModifier().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(Res.string.critical_alerts),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Start,
            )
            Text(
                text = stringResource(Res.string.critical_alerts_dnd_request_text),
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.78f)),
                textAlign = TextAlign.Start,
            )
        }
    }
}
