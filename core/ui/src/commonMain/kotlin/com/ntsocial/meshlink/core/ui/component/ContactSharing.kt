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
@file:Suppress("detekt:ALL")

package com.ntsocial.meshlink.core.ui.component

import androidx.compose.runtime.Composable
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.util.getSharedContactUrl
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.share_contact
import com.ntsocial.meshlink.core.ui.util.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.proto.SharedContact

/**
 * Displays a dialog with the contact's information as a QR code and URI.
 *
 * @param contact The node representing the contact to share. Null if no contact is selected.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun SharedContactDialog(contact: Node?, onDismiss: () -> Unit) {
    if (contact == null) return
    val contactToShare = SharedContact(user = contact.user, node_num = contact.num)
    val commonUri = contactToShare.getSharedContactUrl()
    val uriString = commonUri.toString()
    val qrPainter = rememberQrCodePainter(uriString, 960)
    QrDialog(
        title = stringResource(Res.string.share_contact),
        uriString = uriString,
        qrPainter = qrPainter,
        onDismiss = onDismiss,
    )
}

/**
 * Displays a dialog for importing a shared contact.
 *
 * @param sharedContact The [SharedContact] to import.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun SharedContactImportDialog(sharedContact: SharedContact, onDismiss: () -> Unit) {
    com.ntsocial.meshlink.core.ui.share.SharedContactDialog(sharedContact = sharedContact, onDismiss = onDismiss)
}
