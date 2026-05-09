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
package com.ntsocial.meshlink.core.network.radio

import android.content.Context
import android.hardware.usb.UsbManager
import android.provider.Settings
import com.ntsocial.meshlink.core.ble.BleConnectionFactory
import com.ntsocial.meshlink.core.ble.BleScanner
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.BuildConfigProvider
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.DeviceType
import com.ntsocial.meshlink.core.model.InterfaceId
import com.ntsocial.meshlink.core.network.repository.UsbRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioTransport
import com.ntsocial.meshlink.core.repository.RadioTransportFactory
import org.koin.core.annotation.Single

/**
 * Android implementation of [RadioTransportFactory]. Handles pure-KMP transports (BLE) via [BaseRadioTransportFactory]
 * while creating platform-specific connections (TCP, USB/Serial, Mock, NOP) directly in [createPlatformTransport].
 */
@Single(binds = [RadioTransportFactory::class])
@Suppress("LongParameterList")
class AndroidRadioTransportFactory(
    private val context: Context,
    private val buildConfigProvider: BuildConfigProvider,
    private val usbRepository: UsbRepository,
    private val usbManager: UsbManager,
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override fun isMockTransport(): Boolean =
        buildConfigProvider.isDebug || Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"

    override fun isPlatformAddressValid(address: String): Boolean {
        val interfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) } ?: return false
        val rest = address.substring(1)
        return when (interfaceId) {
            InterfaceId.MOCK,
            InterfaceId.NOP,
            InterfaceId.TCP,
            -> true

            InterfaceId.SERIAL -> {
                val deviceMap = usbRepository.serialDevices.value
                val driver = deviceMap[rest] ?: deviceMap.values.firstOrNull()
                driver != null && usbManager.hasPermission(driver.device)
            }

            InterfaceId.BLUETOOTH -> true // Handled by base class
        }
    }

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport {
        val interfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        val rest = address.substring(1)

        return when (interfaceId) {
            InterfaceId.MOCK -> MockRadioTransport(callback = service, scope = service.serviceScope, address = rest)

            InterfaceId.TCP ->
                TcpRadioTransport(
                    callback = service,
                    scope = service.serviceScope,
                    dispatchers = dispatchers,
                    address = rest,
                )

            InterfaceId.SERIAL ->
                SerialRadioTransport(
                    callback = service,
                    scope = service.serviceScope,
                    usbRepository = usbRepository,
                    address = rest,
                )

            InterfaceId.NOP,
            null,
            -> NopRadioTransport(rest)

            InterfaceId.BLUETOOTH -> error("BLE addresses should be handled by BaseRadioTransportFactory")
        }
    }
}
