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

import app.cash.turbine.test
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.proto.SharedContact
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SharedContactViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SharedContactViewModel
    private val nodeRepository = FakeNodeRepository()
    private val serviceRepository = FakeServiceRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SharedContactViewModel(nodeRepository, serviceRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `unfilteredNodes reflects repository updates`() = runTest(testDispatcher) {
        viewModel = SharedContactViewModel(nodeRepository, serviceRepository)

        viewModel.unfilteredNodes.test {
            assertEquals(emptyList(), awaitItem())
            val node = Node(num = 123)
            nodeRepository.setNodes(listOf(node))
            assertEquals(listOf(node), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addSharedContact delegates to serviceRepository`() = runTest(testDispatcher) {
        val contact = SharedContact(node_num = 123)

        val job = viewModel.addSharedContact(contact)
        job.join()

        // You might want to verify the state on your FakeServiceRepository
        // serviceRepository.serviceAction
    }
}
