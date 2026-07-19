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
package com.ntsocial.meshlink.feature.settings.radio.component

import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.import_label
import com.ntsocial.meshlink.core.resources.play
import com.ntsocial.meshlink.core.resources.ringtone_file_empty
import com.ntsocial.meshlink.core.resources.ringtone_import_error
import com.ntsocial.meshlink.core.resources.ringtone_imported
import com.ntsocial.meshlink.core.ui.icon.FolderOpen
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.PlayArrow
import org.jetbrains.compose.resources.stringResource
import java.io.File

private const val MAX_RINGTONE_SIZE = 230
private const val IMPORT_ERROR_PLACEHOLDER = "@@ERROR@@"

@Suppress("TooGenericExceptionCaught")
@Composable
actual fun RingtoneTrailingIcon(ringtoneInput: String, onRingtoneImported: (String) -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val importedText = stringResource(Res.string.ringtone_imported)
    val emptyText = stringResource(Res.string.ringtone_file_empty)
    // Pre-resolve the format pattern for use in the non-composable launcher callback.
    // Using a sentinel placeholder that will be replaced at call-site.
    val importErrorPrefix = stringResource(Res.string.ringtone_import_error, IMPORT_ERROR_PLACEHOLDER)

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        stream.bufferedReader().use { reader ->
                            val buffer = CharArray(MAX_RINGTONE_SIZE)
                            val read = reader.read(buffer)
                            if (read > 0) {
                                onRingtoneImported(String(buffer, 0, read))
                                Toast.makeText(context, importedText, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, emptyText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Error importing ringtone" }
                    val errorMsg = importErrorPrefix.replace(IMPORT_ERROR_PLACEHOLDER, e.message ?: e.toString())
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }

    Row {
        IconButton(onClick = { launcher.launch("*/*") }, enabled = enabled) {
            Icon(MeshtasticIcons.FolderOpen, contentDescription = stringResource(Res.string.import_label))
        }

        IconButton(
            onClick = {
                try {
                    val tempFile = File.createTempFile("ringtone", ".rtttl", context.cacheDir)
                    tempFile.writeText(ringtoneInput)
                    val mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(tempFile.absolutePath)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    mediaPlayer.setOnCompletionListener {
                        it.release()
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to play ringtone" }
                }
            },
            enabled = enabled,
        ) {
            Icon(MeshtasticIcons.PlayArrow, contentDescription = stringResource(Res.string.play))
        }
    }
}
