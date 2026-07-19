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
package com.ntsocial.meshlink.feature.node.detail

import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeRadioController
import com.ntsocial.meshlink.core.ui.util.AlertManager
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeManagementActionsTest {

    private val nodeRepository = FakeNodeRepository()
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val radioController = FakeRadioController()
    private val alertManager = mock<AlertManager>(MockMode.autofill)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val actions =
        NodeManagementActions(
            nodeRepository = nodeRepository,
            serviceRepository = serviceRepository,
            radioController = radioController,
            alertManager = alertManager,
        )

    @Test
    fun requestRemoveNode_shows_confirmation_alert() {
        val node = Node(num = 123, user = User(long_name = "Test Node"))

        actions.requestRemoveNode(testScope, node)

        verify {
            alertManager.showAlert(
                titleRes = any(),
                messageRes = any(),
                onConfirm = any(),
                onDismiss = any(),
                confirmText = any(),
                confirmTextRes = any(),
                dismissText = any(),
                dismissTextRes = any(),
                choices = any(),
            )
        }
    }

    @Test
    fun requestRemoveNode_invokes_onAfterRemove_when_user_confirms() {
        val realAlertManager = AlertManager()
        val actionsWithRealAlert =
            NodeManagementActions(
                nodeRepository = nodeRepository,
                serviceRepository = serviceRepository,
                radioController = radioController,
                alertManager = realAlertManager,
            )
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false

        actionsWithRealAlert.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()

        assertTrue(afterRemoveCalled)
    }
}
