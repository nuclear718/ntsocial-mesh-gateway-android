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
package com.ntsocial.meshlink.core.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ntsocial.meshlink.core.model.util.compareUsers
import com.ntsocial.meshlink.core.model.util.userFieldsToString
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.import_known_shared_contact_text
import com.ntsocial.meshlink.core.resources.import_label
import com.ntsocial.meshlink.core.resources.import_shared_contact
import com.ntsocial.meshlink.core.resources.public_key_changed
import com.ntsocial.meshlink.core.ui.component.MeshtasticDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/** A dialog for importing a shared contact that was scanned from a QR code. */
@Composable
fun SharedContactDialog(
    sharedContact: SharedContact,
    onDismiss: () -> Unit,
    viewModel: SharedContactViewModel = koinViewModel(),
) {
    val unfilteredNodes by viewModel.unfilteredNodes.collectAsStateWithLifecycle()

    val nodeNum = sharedContact.node_num
    val node = unfilteredNodes.find { it.num == nodeNum }

    MeshtasticDialog(
        titleRes = Res.string.import_shared_contact,
        text = {
            Column {
                if (node != null) {
                    Text(text = stringResource(Res.string.import_known_shared_contact_text))
                    if ((node.user.public_key.size) > 0 && node.user.public_key != sharedContact.user?.public_key) {
                        Text(
                            text = stringResource(Res.string.public_key_changed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider()
                    Text(text = compareUsers(node.user, sharedContact.user ?: User()))
                } else {
                    Text(text = userFieldsToString(sharedContact.user ?: User()))
                }
            }
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.import_label),
        onConfirm = {
            viewModel.addSharedContact(sharedContact)
            onDismiss()
        },
    )
}
