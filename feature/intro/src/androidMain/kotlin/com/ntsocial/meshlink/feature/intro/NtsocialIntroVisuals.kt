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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_left
import com.ntsocial.meshlink.core.resources.img_ntsocial_butterfly_empty
import com.ntsocial.meshlink.core.resources.img_ntsocial_butterfly_wordmark
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal val NtsocialIntroBlue = Color(0xFF3DA8FF)

private val IntroPanelMaxWidth = 380.dp
private val IntroPanelPadding = 24.dp
private const val SPLASH_FADE_MILLIS = 900
private const val SPLASH_EXIT_MILLIS = 700

@Composable
internal fun NtsocialIntroSplashScreen(onFinish: () -> Unit) {
    val currentOnFinish = rememberUpdatedState(onFinish)
    val firstImageAlpha = remember { Animatable(0f) }
    val secondImageAlpha = remember { Animatable(0f) }
    val containerAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        firstImageAlpha.animateTo(1f, animationSpec = tween(durationMillis = SPLASH_FADE_MILLIS))
        secondImageAlpha.animateTo(1f, animationSpec = tween(durationMillis = SPLASH_FADE_MILLIS))
        containerAlpha.animateTo(0f, animationSpec = tween(durationMillis = SPLASH_EXIT_MILLIS))
        currentOnFinish.value()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).alpha(containerAlpha.value)) {
        Image(
            painter = painterResource(Res.drawable.img_ntsocial_butterfly_empty),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(firstImageAlpha.value),
            contentScale = ContentScale.Crop,
        )
        Image(
            painter = painterResource(Res.drawable.img_ntsocial_butterfly_wordmark),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(secondImageAlpha.value),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal fun NtsocialIntroScaffold(
    bottomBar: @Composable () -> Unit,
    background: DrawableResource = Res.drawable.img_ntsocial_background_left,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    Scaffold(containerColor = Color.Black, bottomBar = bottomBar) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Image(
                painter = painterResource(background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            content()
        }
    }
}

internal fun BoxWithConstraintsScope.ntsocialIntroPanelModifier(): Modifier = if (maxWidth < 480.dp) {
    Modifier.align(Alignment.Center).fillMaxWidth().padding(IntroPanelPadding)
} else {
    Modifier.align(Alignment.CenterEnd)
        .padding(end = maxWidth * 0.08f)
        .widthIn(max = IntroPanelMaxWidth)
        .padding(IntroPanelPadding)
}
