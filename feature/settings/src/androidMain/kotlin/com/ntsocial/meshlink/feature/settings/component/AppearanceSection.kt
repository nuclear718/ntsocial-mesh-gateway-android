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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.app_language
import com.ntsocial.meshlink.core.resources.app_settings
import com.ntsocial.meshlink.core.resources.theme
import com.ntsocial.meshlink.core.ui.component.ListItem
import com.ntsocial.meshlink.core.ui.icon.ChevronRight
import com.ntsocial.meshlink.core.ui.icon.FormatPaint
import com.ntsocial.meshlink.core.ui.icon.Language
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/** Section for app appearance settings like language and theme. */
@Composable
fun AppearanceSection(onShowLanguagePicker: () -> Unit, onShowThemePicker: () -> Unit) {
    ExpressiveSection(title = stringResource(Res.string.app_settings)) {
        ListItem(
            text = stringResource(Res.string.app_language),
            leadingIcon = MeshtasticIcons.Language,
            trailingIcon = MeshtasticIcons.ChevronRight,
        ) {
            onShowLanguagePicker()
        }

        ListItem(
            text = stringResource(Res.string.theme),
            leadingIcon = MeshtasticIcons.FormatPaint,
            trailingIcon = null,
        ) {
            onShowThemePicker()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppearanceSectionPreview() {
    AppTheme { AppearanceSection(onShowLanguagePicker = {}, onShowThemePicker = {}) }
}
