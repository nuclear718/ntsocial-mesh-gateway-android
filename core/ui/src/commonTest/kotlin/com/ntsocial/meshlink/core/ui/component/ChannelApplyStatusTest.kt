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
package com.ntsocial.meshlink.core.ui.component

import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.channel_apply_failed_title
import com.ntsocial.meshlink.core.resources.channel_apply_rejected
import com.ntsocial.meshlink.core.ui.util.AlertManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ChannelApplyStatusTest {
    @Test
    fun `timeouts reconnects and session loss remain informational`() {
        assertEquals(
            ChannelApplyUiState.WaitingForReconnect,
            ChannelReliabilityResult.VERIFICATION_PENDING.toChannelApplyUiState(),
        )
        assertEquals(
            ChannelApplyUiState.WaitingForReconnect,
            ChannelReliabilityResult.SESSION_UNAVAILABLE.toChannelApplyUiState(),
        )
        assertEquals(
            ChannelApplyUiState.WaitingForReconnect,
            ChannelReliabilityResult.DISCONNECTED.toChannelApplyUiState(),
        )
    }

    @Test
    fun `explicit NAK remains a terminal failure and uses the global alert`() {
        val result = ChannelReliabilityResult.RADIO_REJECTED
        assertIs<ChannelApplyUiState.Failed>(result.toChannelApplyUiState())

        val alertManager = AlertManager()
        alertManager.showChannelApplyFailure(result)

        assertEquals(Res.string.channel_apply_failed_title, alertManager.currentAlert.value?.titleRes)
        assertEquals(Res.string.channel_apply_rejected, alertManager.currentAlert.value?.messageRes)
    }

    @Test
    fun `mismatched complete readback remains a terminal failure`() {
        assertIs<ChannelApplyUiState.Failed>(ChannelReliabilityResult.READBACK_FAILED.toChannelApplyUiState())
    }

    @Test
    fun `invalid local settings are non modal validation feedback`() {
        val result = ChannelReliabilityResult.INVALID_CHANNEL_SET
        assertEquals(ChannelApplyUiState.InvalidSettings, result.toChannelApplyUiState())

        val alertManager = AlertManager()
        alertManager.showChannelApplyFailure(result)

        assertNull(alertManager.currentAlert.value)
    }
}
