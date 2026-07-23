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
package com.ntsocial.meshlink.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_butterfly
import com.ntsocial.meshlink.core.resources.meshtastic_app_name
import com.ntsocial.meshlink.desktop.branding.WINDOWS_SPLASH_DURATION_MILLIS
import com.ntsocial.meshlink.desktop.branding.windowsSplashVisualState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val SplashBackground = Color(0xFF0E1420)
private val SplashScrim = Color(0x520E1420)
private val SplashText = Color(0xFFF3F5F9)

/** NTsocial Windows cold-start overlay. Runtime services and the main UI continue loading underneath it. */
@Composable
internal fun WindowsBrandSplashOverlay(brandPainter: Painter, onFinish: () -> Unit) {
    val timeline = remember { Animatable(0F) }
    val currentOnFinish = rememberUpdatedState(onFinish)
    val visualState = windowsSplashVisualState(timeline.value.toLong())

    LaunchedEffect(Unit) {
        timeline.animateTo(
            targetValue = WINDOWS_SPLASH_DURATION_MILLIS.toFloat(),
            animationSpec = tween(durationMillis = WINDOWS_SPLASH_DURATION_MILLIS.toInt(), easing = LinearEasing),
        )
        currentOnFinish.value()
    }

    Box(modifier = Modifier.fillMaxSize().alpha(visualState.overlayAlpha).background(SplashBackground)) {
        Image(
            painter = painterResource(Res.drawable.img_ntsocial_background_butterfly),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(visualState.backgroundAlpha),
        )
        Box(modifier = Modifier.fillMaxSize().background(SplashScrim))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).widthIn(max = 420.dp).alpha(visualState.brandAlpha),
        ) {
            Image(
                painter = brandPainter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(192.dp),
            )
            Text(
                text = stringResource(Res.string.meshtastic_app_name),
                color = SplashText,
                style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}
