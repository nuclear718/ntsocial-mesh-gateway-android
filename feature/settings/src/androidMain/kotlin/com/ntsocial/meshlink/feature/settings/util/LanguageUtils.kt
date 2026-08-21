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
package com.ntsocial.meshlink.feature.settings.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.language_english
import com.ntsocial.meshlink.core.resources.language_japanese
import com.ntsocial.meshlink.core.resources.language_traditional_chinese
import org.jetbrains.compose.resources.stringResource

object LanguageUtils {
    val supportedLanguageTags = listOf("en", "zh-TW", "ja")

    /**
     * Sets the application locale using AppCompatDelegate. Note: This is the modern standard for per-app language
     * support, providing a backport for API levels below 33.
     */
    fun setAppLocale(languageTag: String) {
        require(languageTag in supportedLanguageTags) { "Unsupported app language tag" }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /** The same three explicit choices offered by the released NTsocial Android App. */
    @Composable
    fun languageMap(): Map<String, String> {
        val languageTags = remember { supportedLanguageTags }
        return languageTags.associateWith { languageTag ->
            when (languageTag) {
                "en" -> stringResource(Res.string.language_english)
                "zh-TW" -> stringResource(Res.string.language_traditional_chinese)
                else -> stringResource(Res.string.language_japanese)
            }
        }
    }
}
