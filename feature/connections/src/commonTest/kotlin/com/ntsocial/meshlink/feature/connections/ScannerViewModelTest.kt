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
package com.ntsocial.meshlink.feature.connections

import app.cash.turbine.test
import com.ntsocial.meshlink.core.datastore.RecentAddressesDataSource
import com.ntsocial.meshlink.core.network.repository.DiscoveredService
import com.ntsocial.meshlink.core.network.repository.NetworkRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.testing.FakeBleDevice
import com.ntsocial.meshlink.core.testing.FakeRadioController
import com.ntsocial.meshlink.core.testing.FakeServiceRepository
import com.ntsocial.meshlink.feature.connections.model.DeviceListEntry
import com.ntsocial.meshlink.feature.connections.model.DiscoveredDevices
import com.ntsocial.meshlink.feature.connections.model.GetDiscoveredDevicesUseCase
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private lateinit var viewModel: ScannerViewModel
    private val serviceRepository = FakeServiceRepository()
    private val radioController = FakeRadioController()
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val radioPrefs: RadioPrefs = mock(MockMode.autofill)
    private val recentAddressesDataSource: RecentAddressesDataSource = mock(MockMode.autofill)
    private val networkRepository: NetworkRepository = mock(MockMode.autofill)
    private val bleScanner: com.ntsocial.meshlink.core.ble.BleScanner = mock(MockMode.autofill)
    private val uiPrefs = com.ntsocial.meshlink.core.testing.FakeUiPrefs()

    private val resolvedServicesFlow = MutableStateFlow<List<DiscoveredService>>(emptyList())
    private val baseDevicesFlow = MutableStateFlow(DiscoveredDevices())

    /**
     * A fake [GetDiscoveredDevicesUseCase] that mirrors the real behavior: it combines the provided [resolvedList] with
     * base device data so tests can verify NSD gating.
     */
    private val getDiscoveredDevicesUseCase =
        object : GetDiscoveredDevicesUseCase {
            override fun invoke(
                showMock: Boolean,
                resolvedList: Flow<List<DiscoveredService>>,
            ): Flow<DiscoveredDevices> = combine(baseDevicesFlow, resolvedList) { base, resolved ->
                val tcpDevices =
                    resolved.map { DeviceListEntry.Tcp(name = it.name, fullAddress = "t${it.hostAddress}") }
                base.copy(discoveredTcpDevices = tcpDevices)
            }
        }

    @BeforeTest
    fun setUp() {
        every { radioInterfaceService.isMockTransport() } returns false
        every { radioInterfaceService.currentDeviceAddressFlow } returns MutableStateFlow(null)

        every { recentAddressesDataSource.recentAddresses } returns MutableStateFlow(emptyList())
        every { networkRepository.resolvedList } returns resolvedServicesFlow
        every { networkRepository.networkAvailable } returns flowOf(true)

        serviceRepository.setConnectionProgress("")
        baseDevicesFlow.value = DiscoveredDevices()
        resolvedServicesFlow.value = emptyList()

        viewModel =
            ScannerViewModel(
                serviceRepository = serviceRepository,
                radioController = radioController,
                radioInterfaceService = radioInterfaceService,
                radioPrefs = radioPrefs,
                recentAddressesDataSource = recentAddressesDataSource,
                getDiscoveredDevicesUseCase = getDiscoveredDevicesUseCase,
                networkRepository = networkRepository,
                dispatchers =
                com.ntsocial.meshlink.core.di.CoroutineDispatchers(
                    io = UnconfinedTestDispatcher(),
                    main = UnconfinedTestDispatcher(),
                    default = UnconfinedTestDispatcher(),
                ),
                uiPrefs = uiPrefs,
                bleScanner = bleScanner,
            )
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `connectionProgressText reflects connectionProgress`() = runTest {
        viewModel.connectionProgressText.test {
            assertEquals("", awaitItem())
            serviceRepository.setConnectionProgress("Connecting...")
            assertEquals("Connecting...", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startBleScan updates isBleScanning`() = runTest {
        every { bleScanner.scan(any(), any()) } returns kotlinx.coroutines.flow.emptyFlow()

        viewModel.isBleScanning.test {
            assertEquals(false, awaitItem())
            viewModel.startBleScan()
            assertEquals(true, awaitItem())

            viewModel.stopBleScan()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changeDeviceAddress calls radioController`() {
        viewModel.changeDeviceAddress("test_address")

        assertEquals("test_address", radioController.lastSetDeviceAddress)
    }

    @Test
    fun `usbDevicesForUi emits updates`() = runTest {
        viewModel.usbDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            val device =
                DeviceListEntry.Usb(
                    usbData = object : com.ntsocial.meshlink.feature.connections.model.UsbDeviceData {},
                    name = "USB Device",
                    fullAddress = "usb_address",
                    bonded = true,
                )
            baseDevicesFlow.value = DiscoveredDevices(usbDevices = listOf(device))

            assertEquals(listOf(device), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isNetworkScanning defaults to false`() {
        assertEquals(false, viewModel.isNetworkScanning.value)
    }

    @Test
    fun `startNetworkScan updates isNetworkScanning`() = runTest {
        viewModel.isNetworkScanning.test {
            assertEquals(false, awaitItem())
            viewModel.startNetworkScan()
            assertEquals(true, awaitItem())
            viewModel.stopNetworkScan()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discoveredTcpDevicesForUi is empty when not scanning`() = runTest {
        resolvedServicesFlow.value =
            listOf(DiscoveredService(name = "NSD Device", hostAddress = "192.168.1.50", port = 4403))

        viewModel.discoveredTcpDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discoveredTcpDevicesForUi populates when scanning is active`() = runTest {
        resolvedServicesFlow.value =
            listOf(DiscoveredService(name = "NSD Device", hostAddress = "192.168.1.50", port = 4403))

        viewModel.discoveredTcpDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())
            viewModel.startNetworkScan()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("t192.168.1.50", result[0].fullAddress)
            viewModel.stopNetworkScan()
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bleDevicesForUi sorts by bonded then discovery order`() = runTest {
        val device1 = FakeBleDevice(address = "01:02:03:04:05:06", name = "Node B", rssi = -50)
        val device2 = FakeBleDevice(address = "07:08:09:0A:0B:0C", name = "Node A", rssi = -30)
        val bondedDevice =
            DeviceListEntry.Ble(
                device = FakeBleDevice(address = "0D:0E:0F:10:11:12", name = "Bonded C", rssi = null),
                bonded = true,
            )

        val scanFlow = MutableStateFlow<com.ntsocial.meshlink.core.ble.BleDevice?>(null)
        every { bleScanner.scan(any(), any()) } returns scanFlow.filterNotNull()

        viewModel.bleDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            // 1. Bonded device appears (via use case)
            baseDevicesFlow.value = DiscoveredDevices(bleDevices = listOf(bondedDevice))
            assertEquals(listOf(bondedDevice), awaitItem())

            // 2. Scan finds Device 1 (Node B, -50dBm)
            viewModel.startBleScan()
            scanFlow.value = device1
            val itemsAfterDevice1 = awaitItem()
            assertEquals(2, itemsAfterDevice1.size)
            assertEquals(bondedDevice.address, (itemsAfterDevice1[0] as DeviceListEntry.Ble).address)
            assertEquals(device1.address, (itemsAfterDevice1[1] as DeviceListEntry.Ble).address)

            // 3. Scan finds Device 2 (Node A, -30dBm) - stronger signal but should be AFTER Device 1 per discovery
            // order
            scanFlow.value = device2
            val itemsAfterDevice2 = awaitItem()
            assertEquals(3, itemsAfterDevice2.size)
            assertEquals(bondedDevice.address, (itemsAfterDevice2[0] as DeviceListEntry.Ble).address)
            assertEquals(device1.address, (itemsAfterDevice1[1] as DeviceListEntry.Ble).address)
            assertEquals(device2.address, (itemsAfterDevice2[2] as DeviceListEntry.Ble).address)

            // 4. Device 1 RSSI updates to -20dBm (strongest) - should NOT re-sort
            scanFlow.value = FakeBleDevice(address = device1.address, name = device1.name, rssi = -20)
            val itemsAfterRssiUpdate = awaitItem()
            assertEquals(3, itemsAfterRssiUpdate.size)
            assertEquals(device1.address, (itemsAfterRssiUpdate[1] as DeviceListEntry.Ble).address)
            assertEquals(-20, (itemsAfterRssiUpdate[1] as DeviceListEntry.Ble).device.rssi)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopBleScan does not clear scanned devices`() = runTest {
        val device = FakeBleDevice(address = "01:02:03:04:05:06", name = "Node", rssi = -50)
        val scanFlow = MutableStateFlow<com.ntsocial.meshlink.core.ble.BleDevice?>(null)
        every { bleScanner.scan(any(), any()) } returns scanFlow.filterNotNull()

        viewModel.bleDevicesForUi.test {
            assertEquals(emptyList(), awaitItem())

            viewModel.startBleScan()
            scanFlow.value = device
            assertEquals(1, awaitItem().size)

            viewModel.stopBleScan()
            // Should not emit a new empty list
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
