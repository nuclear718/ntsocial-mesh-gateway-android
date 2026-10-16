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
package com.ntsocial.meshlink.feature.connections

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.datastore.RecentAddressesDataSource
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.network.repository.NetworkRepository
import com.ntsocial.meshlink.core.network.repository.UsbRepository
import com.ntsocial.meshlink.core.radiofleet.DiscoveredRadio
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.testing.FakeBleDevice
import com.ntsocial.meshlink.core.testing.FakeRadioController
import com.ntsocial.meshlink.core.testing.FakeServiceRepository
import com.ntsocial.meshlink.core.testing.FakeUiPrefs
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidScannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val serviceRepository = FakeServiceRepository()
    private val bluetoothRepository = mock<BluetoothRepository>(MockMode.autofill)
    private val fleet = mock<RadioFleetManager>(MockMode.autofill)
    private val entry = DeviceListEntry.Ble(FakeBleDevice("AA:BB:CC:DD:EE:01"), bonded = false)
    private val profile =
        RadioEndpointProfile(RadioEndpointId("test-radio"), RadioProtocol.MESHTASTIC, entry.fullAddress, entry.name)
    private var registrations = 0
    private lateinit var viewModel: AndroidScannerViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val radioInterface = mock<RadioInterfaceService>(MockMode.autofill)
        val recentAddresses = mock<RecentAddressesDataSource>(MockMode.autofill)
        val network = mock<NetworkRepository>(MockMode.autofill)
        val lifecycle = LifecycleRegistry.createUnsafe(mock<LifecycleOwner>())
        lifecycle.currentState = Lifecycle.State.CREATED
        lifecycle.currentState = Lifecycle.State.DESTROYED
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        every { radioInterface.currentDeviceAddressFlow } returns MutableStateFlow(null)
        every { recentAddresses.recentAddresses } returns MutableStateFlow(emptyList())
        every { network.resolvedList } returns MutableStateFlow(emptyList())
        everySuspend { fleet.register(any(), any()) } calls
            {
                registrations++
                profile
            }
        viewModel =
            AndroidScannerViewModel(
                serviceRepository = serviceRepository,
                radioController = FakeRadioController(),
                radioInterfaceService = radioInterface,
                radioPrefs = mock(MockMode.autofill),
                recentAddressesDataSource = recentAddresses,
                getDiscoveredDevicesUseCase = mock(MockMode.autofill),
                networkRepository = network,
                dispatchers = dispatchers,
                bluetoothRepository = bluetoothRepository,
                usbRepository =
                UsbRepository(
                    application = ApplicationProvider.getApplicationContext(),
                    dispatchers = dispatchers,
                    processLifecycle = lifecycle,
                    usbBroadcastReceiverLazy = lazy { error("USB is not used by BLE pairing") },
                    usbManagerLazy = lazy { null },
                    usbSerialProberLazy = lazy { error("USB is not used by BLE pairing") },
                ),
                uiPrefs = FakeUiPrefs(),
                radioFleetManager = fleet,
            )
        store.put("scanner", viewModel)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `disposing the pairing screen cancels its waiter without publishing a bonding failure`() = runTest(dispatcher) {
        var waiting = false
        var cleanedUp = false
        everySuspend { bluetoothRepository.bond(entry.device) } calls
            {
                waiting = true
                try {
                    awaitCancellation()
                } finally {
                    cleanedUp = true
                }
            }

        viewModel.onSelected(entry)
        runCurrent()
        assertTrue(waiting)
        store.clear()
        runCurrent()

        assertTrue(cleanedUp)
        assertNull(serviceRepository.errorMessage.value)
        assertEquals(0, registrations)
    }

    @Test
    fun `repository cancellation does not become a warning or start a connection`() = runTest(dispatcher) {
        everySuspend { bluetoothRepository.bond(entry.device) } throws CancellationException("Job was cancelled")

        viewModel.onSelected(entry)
        runCurrent()

        assertNull(serviceRepository.errorMessage.value)
        assertEquals(0, registrations)
    }

    @Test
    fun `successful pairing hands the selected device to the fleet`() = runTest(dispatcher) {
        viewModel.onSelected(entry)
        runCurrent()

        verifySuspend {
            bluetoothRepository.bond(entry.device)
            fleet.register(DiscoveredRadio(transportAddress = entry.fullAddress, displayName = entry.name), false)
            fleet.connect(profile.id, null)
        }
        assertEquals(1, registrations)
        assertNull(serviceRepository.errorMessage.value)
    }

    @Test
    fun `rejected pairing still reports failure and does not connect`() = runTest(dispatcher) {
        everySuspend { bluetoothRepository.bond(entry.device) } throws
            IllegalStateException("Bonding failed or rejected")

        viewModel.onSelected(entry)
        runCurrent()

        assertEquals("Bonding failed: Bonding failed or rejected", serviceRepository.errorMessage.value)
        assertEquals(0, registrations)
    }

    @Test
    fun `missing Bluetooth permission still reports failure and does not connect`() = runTest(dispatcher) {
        everySuspend { bluetoothRepository.bond(entry.device) } throws
            SecurityException("Bluetooth permission missing")

        viewModel.onSelected(entry)
        runCurrent()

        assertEquals(
            "Bonding failed: Bluetooth permission missing Permissions not granted",
            serviceRepository.errorMessage.value,
        )
        assertEquals(0, registrations)
    }
}
