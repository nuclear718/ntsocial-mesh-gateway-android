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
package com.ntsocial.meshlink.app.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.analytics_notice
import com.ntsocial.meshlink.core.resources.analytics_platforms
import com.ntsocial.meshlink.core.resources.datadog_link
import com.ntsocial.meshlink.core.resources.firebase_crashlytics_link
import com.ntsocial.meshlink.core.resources.for_more_information_see_our_privacy_policy
import com.ntsocial.meshlink.core.resources.privacy_url
import com.ntsocial.meshlink.core.ui.component.AutoLinkText
import org.jetbrains.compose.resources.stringResource

@Composable
fun AnalyticsIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = stringResource(Res.string.analytics_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center,
            text = stringResource(Res.string.analytics_platforms),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AutoLinkText(
            text = stringResource(Res.string.firebase_crashlytics_link),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        AutoLinkText(
            text = stringResource(Res.string.datadog_link),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center,
            text = stringResource(Res.string.for_more_information_see_our_privacy_policy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AutoLinkText(
            text = stringResource(Res.string.privacy_url),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsIntroPreview() {
    AnalyticsIntro()
}
