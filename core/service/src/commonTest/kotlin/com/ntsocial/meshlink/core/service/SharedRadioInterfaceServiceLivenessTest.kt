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
package com.ntsocial.meshlink.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.network.repository.NetworkRepository
import com.ntsocial.meshlink.core.repository.PlatformAnalytics
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.RadioTransport
import com.ntsocial.meshlink.core.repository.RadioTransportFactory
import com.ntsocial.meshlink.core.testing.FakeBluetoothRepository
import com.ntsocial.meshlink.core.testing.FakeRadioPrefs
import com.ntsocial.meshlink.core.testing.FakeRadioTransport
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for BLE zombie sessions: the platform can retain a nominal GATT connection while no radio bytes
 * arrive. The service must recreate the transport without showing a transient failure dialog or sending data into the
 * stale transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedRadioInterfaceServiceLivenessTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    private lateinit var processLifecycleOwner: TestLifecycleOwner

    private val bluetoothRepository = FakeBluetoothRepository()
    private val radioPrefs = FakeRadioPrefs()
    private val networkRepository: NetworkRepository = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)
    private val transportFactory: RadioTransportFactory = mock(MockMode.autofill)

    private var clock = 0L
    private val createdTransports = mutableListOf<FakeRadioTransport>()
    private var activeCloseGate: CompletableDeferred<Unit>? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        processLifecycleOwner = TestLifecycleOwner()
    }

    @AfterTest
    fun tearDown() {
        activeCloseGate?.complete(Unit)
        activeCloseGate = null
        createdTransports.clear()
        processLifecycleOwner.destroy()
        testDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    /**
     * Minimal lifecycle owner that avoids LifecycleRegistry's main-thread restriction in host/JVM tests while still
     * dispatching ON_DESTROY to cancel lifecycleScope collectors.
     */
    private class TestLifecycleOwner : LifecycleOwner {
        private val observers = mutableListOf<LifecycleObserver>()
        private var state = Lifecycle.State.RESUMED

        override val lifecycle: Lifecycle =
            object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    observers.add(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    observers.remove(observer)
                }

                override val currentState: Lifecycle.State
                    get() = state
            }

        fun destroy() {
            state = Lifecycle.State.DESTROYED
            observers.toList().forEach { observer ->
                (observer as? LifecycleEventObserver)?.onStateChanged(
                    this@TestLifecycleOwner,
                    Lifecycle.Event.ON_DESTROY,
                )
            }
        }
    }

    /** Holds close() in flight so overlapping liveness checks can be exercised deterministically. */
    private class GatedFakeRadioTransport(private val closeGate: CompletableDeferred<Unit>) : RadioTransport {
        var closeCalled = false
            private set

        var closeCount = 0
            private set

        var closeCompletedCount = 0
            private set

        override fun handleSendToRadio(p: ByteArray) = Unit

        override suspend fun close() {
            closeCalled = true
            closeCount++
            closeGate.await()
            closeCompletedCount++
        }
    }

    private class CallbackHoldingTransport(val callback: RadioInterfaceService) : RadioTransport {
        override fun handleSendToRadio(p: ByteArray) = Unit

        override suspend fun close() = Unit
    }

    private class GatedSendRadioTransport(
        private val sendStarted: CompletableDeferred<Unit>,
        private val releaseSend: CompletableDeferred<Unit>,
    ) : RadioTransport {
        val sentData = mutableListOf<ByteArray>()
        var gateNextSend = false

        override fun handleSendToRadio(p: ByteArray) {
            if (gateNextSend) {
                gateNextSend = false
                runBlocking {
                    sendStarted.complete(Unit)
                    releaseSend.await()
                }
            }
            sentData += p
        }

        override suspend fun close() = Unit
    }

    private fun createConnectedService(
        address: String,
        transportProvider: () -> RadioTransport = { FakeRadioTransport().also { createdTransports.add(it) } },
    ): SharedRadioInterfaceService {
        every { networkRepository.networkAvailable } returns MutableStateFlow(true)
        every { networkRepository.resolvedList } returns MutableSharedFlow()
        every { analytics.isPlatformServicesAvailable } returns false
        every { transportFactory.supportedDeviceTypes } returns listOf(DeviceType.BLE)
        every { transportFactory.isMockTransport() } returns false
        every { transportFactory.isAddressValid(any()) } returns true
        every { transportFactory.toInterfaceAddress(any(), any()) } returns address
        every { transportFactory.createTransport(any(), any()) } calls { transportProvider() }

        radioPrefs.setDevAddr(address)
        val service =
            SharedRadioInterfaceService(
                dispatchers = dispatchers,
                bluetoothRepository = bluetoothRepository,
                networkRepository = networkRepository,
                processLifecycle = processLifecycleOwner.lifecycle,
                radioPrefs = radioPrefs,
                transportFactory = transportFactory,
                analytics = analytics,
            )
        service.clockMillis = { clock }
        service.connect()
        service.onConnect()
        return service
    }

    @Test
    fun `authoritative hydration ignores late initial null and never starts transport early`() =
        runTest(testDispatcher) {
            val projectedAddress = MutableStateFlow<String?>(null)
            val hydratedPrefs =
                object : RadioPrefs {
                    override val devAddr = projectedAddress
                    override val devName = MutableStateFlow<String?>(null)

                    override suspend fun readPersistedDevAddr(): String = HYDRATED_ADDRESS

                    override fun setDevAddr(address: String?) {
                        projectedAddress.value = address
                    }

                    override fun setDevName(name: String?) = Unit
                }
            val transportAddresses = mutableListOf<String>()
            every { networkRepository.networkAvailable } returns MutableStateFlow(true)
            every { networkRepository.resolvedList } returns MutableSharedFlow()
            every { analytics.isPlatformServicesAvailable } returns false
            every { transportFactory.supportedDeviceTypes } returns listOf(DeviceType.BLE)
            every { transportFactory.isMockTransport() } returns false
            every { transportFactory.isAddressValid(any()) } returns true
            every { transportFactory.createTransport(any(), any()) } calls
                { call ->
                    transportAddresses += call.arg<String>(0)
                    FakeRadioTransport()
                }
            val service =
                SharedRadioInterfaceService(
                    dispatchers = dispatchers,
                    bluetoothRepository = bluetoothRepository,
                    networkRepository = networkRepository,
                    processLifecycle = processLifecycleOwner.lifecycle,
                    radioPrefs = hydratedPrefs,
                    transportFactory = transportFactory,
                    analytics = analytics,
                )

            assertEquals(HYDRATED_ADDRESS, service.awaitHydratedDeviceAddress())
            assertEquals(HYDRATED_ADDRESS, service.getDeviceAddress())
            assertTrue(transportAddresses.isEmpty())

            projectedAddress.value = null
            testDispatcher.scheduler.runCurrent()
            assertEquals(HYDRATED_ADDRESS, service.getDeviceAddress())
            assertTrue(transportAddresses.isEmpty())

            service.connect()
            testDispatcher.scheduler.runCurrent()
            assertEquals(listOf(HYDRATED_ADDRESS), transportAddresses)
            service.disconnect()
        }

    @Test
    fun `retired transport callbacks cannot impersonate replacement session`() = runTest(testDispatcher) {
        val transports = mutableMapOf<String, CallbackHoldingTransport>()
        every { networkRepository.networkAvailable } returns MutableStateFlow(true)
        every { networkRepository.resolvedList } returns MutableSharedFlow()
        every { analytics.isPlatformServicesAvailable } returns false
        every { transportFactory.supportedDeviceTypes } returns listOf(DeviceType.BLE)
        every { transportFactory.isMockTransport() } returns false
        every { transportFactory.isAddressValid(any()) } returns true
        every { transportFactory.createTransport(any(), any()) } calls
            { call ->
                CallbackHoldingTransport(call.arg(1)).also { transports[call.arg(0)] = it }
            }
        radioPrefs.setDevAddr(RADIO_A)
        val service =
            SharedRadioInterfaceService(
                dispatchers = dispatchers,
                bluetoothRepository = bluetoothRepository,
                networkRepository = networkRepository,
                processLifecycle = processLifecycleOwner.lifecycle,
                radioPrefs = radioPrefs,
                transportFactory = transportFactory,
                analytics = analytics,
            )
        service.connect()
        testDispatcher.scheduler.runCurrent()
        val retiredCallback = transports.getValue(RADIO_A).callback
        retiredCallback.onConnect()

        service.setDeviceAddress(RADIO_B)
        // The public selection call must revoke A synchronously, before its async close/start coroutine runs.
        val switchingSession = service.radioSessionState.value
        retiredCallback.onConnect()
        retiredCallback.handleFromRadio(byteArrayOf(1))
        retiredCallback.onDisconnect(isPermanent = true)
        assertEquals(switchingSession, service.radioSessionState.value)

        advanceTimeBy(1_000L)
        testDispatcher.scheduler.runCurrent()
        val currentCallback = transports.getValue(RADIO_B).callback
        val replacementBeforeConnect = service.radioSessionState.value
        val received = async(start = CoroutineStart.UNDISPATCHED) { service.receivedData.first() }

        retiredCallback.onConnect()
        retiredCallback.handleFromRadio(byteArrayOf(1))
        retiredCallback.onDisconnect(isPermanent = true)

        assertEquals(replacementBeforeConnect, service.radioSessionState.value)
        assertFalse(received.isCompleted)

        currentCallback.onConnect()
        currentCallback.handleFromRadio(byteArrayOf(2))
        assertEquals(byteArrayOf(2).toList(), received.await().toList())
        assertEquals(ConnectionState.Connected, service.connectionState.value)

        val connectedSession = service.radioSessionState.value
        retiredCallback.onDisconnect(isPermanent = true)
        retiredCallback.onConnect()
        assertEquals(connectedSession, service.radioSessionState.value)
        assertEquals(ConnectionState.Connected, service.connectionState.value)
        service.disconnect()
    }

    @Test
    fun `callback validated before stop is serialized ahead of reset and cannot leak buffered bytes`() =
        runTest(testDispatcher) {
            val transports = mutableListOf<CallbackHoldingTransport>()
            every { networkRepository.networkAvailable } returns MutableStateFlow(true)
            every { networkRepository.resolvedList } returns MutableSharedFlow()
            every { analytics.isPlatformServicesAvailable } returns false
            every { transportFactory.supportedDeviceTypes } returns listOf(DeviceType.BLE)
            every { transportFactory.isMockTransport() } returns false
            every { transportFactory.isAddressValid(any()) } returns true
            every { transportFactory.createTransport(any(), any()) } calls
                { call ->
                    CallbackHoldingTransport(call.arg(1)).also(transports::add)
                }
            radioPrefs.setDevAddr(RADIO_A)
            val service =
                SharedRadioInterfaceService(
                    dispatchers = dispatchers,
                    bluetoothRepository = bluetoothRepository,
                    networkRepository = networkRepository,
                    processLifecycle = processLifecycleOwner.lifecycle,
                    radioPrefs = radioPrefs,
                    transportFactory = transportFactory,
                    analytics = analytics,
                )
            service.connect()
            testDispatcher.scheduler.runCurrent()
            val callback = transports.single().callback
            callback.onConnect()
            callback.onDisconnect(isPermanent = true)

            val validated = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            service.generationCallbackValidatedHook = {
                runBlocking {
                    validated.complete(Unit)
                    release.await()
                }
            }
            val callbackJob = backgroundScope.launch(Dispatchers.Default) { callback.handleFromRadio(byteArrayOf(9)) }
            validated.await()
            val stopStarted = CompletableDeferred<Unit>()
            val stopAndReset =
                backgroundScope.async(Dispatchers.Default) {
                    stopStarted.complete(Unit)
                    service.disconnect()
                    service.resetReceivedBuffer()
                }
            stopStarted.await()
            assertFalse(stopAndReset.isCompleted)

            release.complete(Unit)
            callbackJob.join()
            stopAndReset.await()
            service.generationCallbackValidatedHook = null

            val next = async(start = CoroutineStart.UNDISPATCHED) { service.receivedData.first() }
            testDispatcher.scheduler.runCurrent()
            assertFalse(next.isCompleted)
            next.cancel()
            assertEquals(ConnectionState.Disconnected, service.connectionState.value)
        }

    @Test
    fun `BLE liveness timeout closes old transport and creates fresh one`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            assertEquals(1, createdTransports.size)

            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(2, createdTransports.size)
            assertTrue(createdTransports.first().closeCalled)
            assertEquals(1, createdTransports.first().closeCount)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    private companion object {
        const val HYDRATED_ADDRESS = "xAA:BB:CC:DD:EE:FF"
        const val RADIO_A = "xAA:AA:AA:AA:AA:AA"
        const val RADIO_B = "xBB:BB:BB:BB:BB:BB"
    }

    @Test
    fun `BLE liveness restart does not emit permanent Disconnected`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            val stateEmissions = mutableListOf<ConnectionState>()
            val collectJob = backgroundScope.launch { service.connectionState.collect { stateEmissions.add(it) } }

            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            collectJob.cancel()

            assertFalse(ConnectionState.Disconnected in stateEmissions)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness restart does not emit user-facing connection error`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            val errors = mutableListOf<String>()
            val collectJob = backgroundScope.launch { service.connectionError.collect { errors.add(it) } }

            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            collectJob.cancel()

            assertTrue(errors.isEmpty())
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness restart does not send polite disconnect into zombie transport`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            val oldTransport = createdTransports.first()
            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertTrue(oldTransport.sentData.isEmpty())
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE repeated liveness checks do not stack restarts`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            clock = 66_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(1, createdTransports.first().closeCount)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE in-flight liveness restart prevents overlapping restart`() = runTest(testDispatcher) {
        val gatedTransports = mutableListOf<GatedFakeRadioTransport>()
        val closeGate = CompletableDeferred<Unit>()
        activeCloseGate = closeGate
        val transportProvider: () -> RadioTransport = {
            GatedFakeRadioTransport(closeGate).also { gatedTransports.add(it) }
        }

        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF", transportProvider)
        try {
            val initialTransport = gatedTransports.first()
            clock = 65_000L
            service.checkLiveness()
            clock = 65_001L
            service.checkLiveness()

            try {
                assertEquals(1, gatedTransports.size)
                assertTrue(initialTransport.closeCalled)
                assertEquals(1, initialTransport.closeCount)
                assertEquals(0, initialTransport.closeCompletedCount)
            } finally {
                closeGate.complete(Unit)
            }

            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            assertEquals(2, gatedTransports.size)
            assertEquals(1, initialTransport.closeCount)
            assertEquals(1, initialTransport.closeCompletedCount)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `non-BLE liveness timeout does not close transport or change state`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("t192.168.1.100")
        try {
            val stateBefore = service.connectionState.value
            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertEquals(stateBefore, service.connectionState.value)
            assertFalse(createdTransports.first().closeCalled)
            assertEquals(1, createdTransports.size)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `inbound data resets the liveness timer`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            clock = 30_000L
            service.handleFromRadio(byteArrayOf(1, 2, 3))

            clock = 60_000L
            service.checkLiveness()
            assertFalse(createdTransports.first().closeCalled)

            clock = 96_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)
            assertTrue(createdTransports.first().closeCalled)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `BLE liveness does not fire when connection state is not Connected`() = runTest(testDispatcher) {
        clock = 0L
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            service.onDisconnect(isPermanent = true)
            clock = 65_000L
            service.checkLiveness()
            testDispatcher.scheduler.runCurrent()
            advanceTimeBy(1_000L)

            assertFalse(createdTransports.first().closeCalled)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `explicit disconnect prevents Bluetooth state recovery from resurrecting the transport`() =
        runTest(testDispatcher) {
            clock = 0L
            val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
            try {
                service.disconnect()
                advanceTimeBy(1_000L)

                bluetoothRepository.setBluetoothEnabled(false)
                bluetoothRepository.setBluetoothEnabled(true)
                testDispatcher.scheduler.runCurrent()

                assertEquals(1, createdTransports.size)
                assertTrue(createdTransports.first().closeCalled)
            } finally {
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    @Test
    fun `selecting a new radio invalidates configured session before old transport teardown`() =
        runTest(testDispatcher) {
            val oldAddress = "xAA:BB:CC:DD:EE:FF"
            val newAddress = "x11:22:33:44:55:66"
            val service = createConnectedService(oldAddress)
            try {
                service.markCurrentSessionConfigured(service.radioSessionState.value.epoch)
                val readySession = service.radioSessionState.value
                assertTrue(readySession.isConfiguredReady)

                assertTrue(service.setDeviceAddress(newAddress))

                val switchingSession = service.radioSessionState.value
                assertTrue(switchingSession.epoch > readySession.epoch)
                assertEquals(newAddress, switchingSession.selectedDeviceAddress)
                assertEquals(null, switchingSession.activeDeviceAddress)
                assertFalse(switchingSession.configured)
                assertFalse(switchingSession.isConfiguredReady)
            } finally {
                advanceTimeBy(1_000L)
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    @Test
    fun `exact raw send rejects retired epoch after same-address replacement is ready`() = runTest(testDispatcher) {
        val service = createConnectedService(RADIO_A)
        try {
            assertTrue(service.markCurrentSessionConfigured(service.radioSessionState.value.epoch))
            val retiredEpoch = service.radioSessionState.value.epoch

            // Reconnect the same selected address through a full stop/start so the new transport is
            // indistinguishable
            // by address but owns a different session epoch and transport token.
            service.disconnect()
            service.connect()
            advanceTimeBy(1_000L)
            testDispatcher.scheduler.runCurrent()
            service.onConnect()
            assertTrue(service.markCurrentSessionConfigured(service.radioSessionState.value.epoch))
            val replacementEpoch = service.radioSessionState.value.epoch
            assertTrue(replacementEpoch > retiredEpoch)
            val sendsBefore = createdTransports.map { it.sentData.size }

            assertFalse(service.sendToRadioForSession(byteArrayOf(7), retiredEpoch))
            testDispatcher.scheduler.runCurrent()
            assertEquals(sendsBefore, createdTransports.map { it.sentData.size })

            assertTrue(service.sendToRadioForSession(byteArrayOf(8), replacementEpoch))
            testDispatcher.scheduler.runCurrent()
            assertEquals(byteArrayOf(8).toList(), createdTransports.last().sentData.last().toList())
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `exact raw dispatch is serialized before same transport object reconnects`() = runTest(testDispatcher) {
        val sendStarted = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()
        val transport = GatedSendRadioTransport(sendStarted, releaseSend)
        val service = createConnectedService(RADIO_A) { transport }
        try {
            assertTrue(service.markCurrentSessionConfigured(service.radioSessionState.value.epoch))
            val retiredEpoch = service.radioSessionState.value.epoch
            val payload = byteArrayOf(42)
            transport.gateNextSend = true

            val exactSend =
                backgroundScope.async(Dispatchers.Default) { service.sendToRadioForSession(payload, retiredEpoch) }
            sendStarted.await()
            val disconnectStarted = CompletableDeferred<Unit>()
            val reconnect =
                backgroundScope.async(Dispatchers.Default) {
                    disconnectStarted.complete(Unit)
                    service.disconnect()
                    service.connect()
                }
            disconnectStarted.await()
            assertFalse(reconnect.isCompleted)

            releaseSend.complete(Unit)
            assertTrue(exactSend.await())
            reconnect.await()
            testDispatcher.scheduler.runCurrent()
            service.onConnect()
            assertTrue(service.markCurrentSessionConfigured(service.radioSessionState.value.epoch))

            assertTrue(service.radioSessionState.value.epoch > retiredEpoch)
            assertEquals(payload.toList(), transport.sentData.first().toList())
        } finally {
            releaseSend.complete(Unit)
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }

    @Test
    fun `disconnect and reconnect callbacks rotate session and require configuration again`() =
        runTest(testDispatcher) {
            val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
            try {
                service.markCurrentSessionConfigured(service.radioSessionState.value.epoch)
                val readySession = service.radioSessionState.value
                assertTrue(readySession.isConfiguredReady)

                service.onDisconnect(isPermanent = false)
                val sleepingSession = service.radioSessionState.value
                assertTrue(sleepingSession.epoch > readySession.epoch)
                assertEquals(ConnectionState.DeviceSleep, sleepingSession.transportConnectionState)
                assertFalse(sleepingSession.configured)

                service.onConnect()
                val reconnectedSession = service.radioSessionState.value
                assertTrue(reconnectedSession.epoch > sleepingSession.epoch)
                assertEquals(ConnectionState.Connected, reconnectedSession.transportConnectionState)
                assertFalse(reconnectedSession.isConfiguredReady)

                service.markCurrentSessionConfigured(readySession.epoch)
                assertFalse(service.radioSessionState.value.isConfiguredReady)

                service.markCurrentSessionConfigured(service.radioSessionState.value.epoch)
                assertTrue(service.radioSessionState.value.isConfiguredReady)
            } finally {
                service.disconnect()
                advanceTimeBy(1_000L)
            }
        }

    @Test
    fun `Bluetooth off and on rotates session and requires fresh configuration`() = runTest(testDispatcher) {
        val service = createConnectedService("xAA:BB:CC:DD:EE:FF")
        try {
            service.markCurrentSessionConfigured(service.radioSessionState.value.epoch)
            val readySession = service.radioSessionState.value

            bluetoothRepository.setBluetoothEnabled(false)
            advanceTimeBy(1_000L)
            val bluetoothOffSession = service.radioSessionState.value
            assertTrue(bluetoothOffSession.epoch > readySession.epoch)
            assertFalse(bluetoothOffSession.isConfiguredReady)

            bluetoothRepository.setBluetoothEnabled(true)
            testDispatcher.scheduler.runCurrent()
            val restartingSession = service.radioSessionState.value
            assertTrue(restartingSession.epoch > bluetoothOffSession.epoch)
            assertEquals(ConnectionState.Connecting, restartingSession.transportConnectionState)
            assertFalse(restartingSession.configured)

            service.onConnect()
            service.markCurrentSessionConfigured(service.radioSessionState.value.epoch)
            assertTrue(service.radioSessionState.value.isConfiguredReady)
        } finally {
            service.disconnect()
            advanceTimeBy(1_000L)
        }
    }
}
