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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.img_ntsocial_background_left
import com.ntsocial.meshlink.core.resources.img_ntsocial_flag_jp
import com.ntsocial.meshlink.core.resources.img_ntsocial_flag_tw
import com.ntsocial.meshlink.core.resources.img_ntsocial_flag_us
import com.ntsocial.meshlink.core.resources.language_english
import com.ntsocial.meshlink.core.resources.language_japanese
import com.ntsocial.meshlink.core.resources.language_traditional_chinese
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The shared first-install language selector used by Android and iOS with the same layout and source assets. */
@Composable
fun LanguageSelectScreen(currentTag: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var selected by remember(currentTag) { mutableStateOf(currentTag.takeIf { it.isNotBlank() } ?: ENGLISH_TAG) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.img_ntsocial_background_left),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        val rightPadding = (maxWidth.value * LANGUAGE_PANEL_RIGHT_OFFSET).dp

        Column(
            modifier =
            Modifier.align(Alignment.CenterEnd)
                .padding(end = rightPadding)
                .widthIn(max = LANGUAGE_PANEL_MAX_WIDTH)
                .padding(LANGUAGE_PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(LANGUAGE_CHOICE_SPACING, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            LanguageChoice(
                label = Res.string.language_english,
                tag = ENGLISH_TAG,
                selected = selected,
                onChange = {
                    selected = it
                    onSelect(it)
                },
                flag = Res.drawable.img_ntsocial_flag_us,
            )
            LanguageChoice(
                label = Res.string.language_traditional_chinese,
                tag = TRADITIONAL_CHINESE_TAG,
                selected = selected,
                onChange = {
                    selected = it
                    onSelect(it)
                },
                flag = Res.drawable.img_ntsocial_flag_tw,
            )
            LanguageChoice(
                label = Res.string.language_japanese,
                tag = JAPANESE_TAG,
                selected = selected,
                onChange = {
                    selected = it
                    onSelect(it)
                },
                flag = Res.drawable.img_ntsocial_flag_jp,
            )
        }
    }
}

@Composable
private fun LanguageChoice(
    label: StringResource,
    tag: String,
    selected: String,
    onChange: (String) -> Unit,
    flag: DrawableResource,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LANGUAGE_CHOICE_CONTENT_SPACING),
        modifier = Modifier.heightIn(min = LANGUAGE_CHOICE_MIN_HEIGHT).clickable { onChange(tag) },
    ) {
        RadioButton(
            selected = selected.equals(tag, ignoreCase = true),
            onClick = { onChange(tag) },
            colors =
            RadioButtonDefaults.colors(
                selectedColor = NtsocialIntroBlue,
                unselectedColor = NtsocialIntroBlue.copy(alpha = 0.6f),
                disabledSelectedColor = NtsocialIntroBlue.copy(alpha = 0.5f),
                disabledUnselectedColor = NtsocialIntroBlue.copy(alpha = 0.3f),
            ),
        )

        Image(
            painter = painterResource(flag),
            contentDescription = null,
            modifier = Modifier.size(LANGUAGE_FLAG_SIZE).clip(RoundedCornerShape(LANGUAGE_FLAG_CORNER_RADIUS)),
            contentScale = ContentScale.Crop,
        )

        Text(text = stringResource(label), color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLanguageSelect() {
    LanguageSelectScreen(currentTag = ENGLISH_TAG, onSelect = {})
}

private const val ENGLISH_TAG = "en"
private const val TRADITIONAL_CHINESE_TAG = "zh-TW"
private const val JAPANESE_TAG = "ja"
private const val LANGUAGE_PANEL_RIGHT_OFFSET = 0.30f
private val LANGUAGE_PANEL_MAX_WIDTH = 360.dp
private val LANGUAGE_PANEL_PADDING = 24.dp
private val LANGUAGE_CHOICE_SPACING = 16.dp
private val LANGUAGE_CHOICE_CONTENT_SPACING = 12.dp
private val LANGUAGE_CHOICE_MIN_HEIGHT = 48.dp
private val LANGUAGE_FLAG_SIZE = 24.dp
private val LANGUAGE_FLAG_CORNER_RADIUS = 2.dp
