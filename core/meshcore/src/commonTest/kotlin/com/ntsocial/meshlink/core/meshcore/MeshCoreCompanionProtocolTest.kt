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
package com.ntsocial.meshlink.core.meshcore

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class MeshCoreCompanionProtocolTest {
    @Test
    fun `app start advertises target protocol three and application name`() {
        val frame = MeshCoreCompanionProtocol.appStart("MeshLink")

        assertContentEquals(byteArrayOf(1, 3, 0, 0, 0, 0, 0, 0) + "MeshLink".encodeToByteArray(), frame)
    }

    @Test
    fun `device query and sync commands match official command codes`() {
        assertContentEquals(byteArrayOf(22, 3), MeshCoreCompanionProtocol.deviceQuery())
        assertContentEquals(byteArrayOf(10), MeshCoreCompanionProtocol.syncNextMessage())
        assertContentEquals(byteArrayOf(20), MeshCoreCompanionProtocol.getBatteryAndStorage())
    }

    @Test
    fun `radio parameters use explicit little endian units`() {
        val frame =
            MeshCoreCompanionProtocol.setRadioParameters(
                frequencyKhz = 917_375,
                bandwidthHz = 62_500,
                spreadingFactor = 7,
                codingRate = 5,
                repeatEnabled = false,
            )

        assertContentEquals(byteArrayOf(11, 127, -1, 13, 0, 36, -12, 0, 0, 7, 5, 0), frame)
    }

    @Test
    fun `channel command preserves a complete sixteen byte secret`() {
        val secret = ByteArray(16) { it.toByte() }
        val frame = MeshCoreCompanionProtocol.setChannel(index = 2, name = "#ntsocial", secret = secret)

        assertEquals(50, frame.size)
        assertEquals(32, frame[0].toInt())
        assertEquals(2, frame[1].toInt())
        assertContentEquals("#ntsocial".encodeToByteArray(), frame.copyOfRange(2, 11))
        assertContentEquals(secret, frame.copyOfRange(34, 50))
    }

    @Test
    fun `oversized channel message is rejected before transport`() {
        assertFailsWith<IllegalArgumentException> {
            MeshCoreCompanionProtocol.sendChannelMessage(0, 1, "x".repeat(MeshCoreCompanionProtocol.MAX_FRAME_SIZE))
        }
    }

    @Test
    fun `self info response decodes current companion fields`() {
        val frame =
            byteArrayOf(5, 1, 22, 30) +
                ByteArray(32) { it.toByte() } +
                int32(-25_000_000) +
                int32(121_500_000) +
                byteArrayOf(2, 1, 0b10_01_11, 1) +
                uint32(923_000) +
                uint32(62_500) +
                byteArrayOf(7, 5) +
                "NTsocial MC".encodeToByteArray()

        val parsed = assertIs<MeshCoreFrame.SelfInfo>(MeshCoreCompanionProtocol.parseFrame(frame)).info

        assertEquals("NTsocial MC", parsed.name)
        assertEquals(MeshCoreContactType.CHAT, parsed.advertType)
        assertEquals(-25_000_000, parsed.advertisedLatitudeE6)
        assertEquals(121_500_000, parsed.advertisedLongitudeE6)
        assertEquals(923_000, parsed.radio.frequencyKhz)
        assertEquals(62_500, parsed.radio.bandwidthHz)
        assertEquals(7, parsed.radio.spreadingFactor)
        assertEquals(5, parsed.radio.codingRate)
        assertEquals(3, parsed.telemetryModeBase)
        assertEquals(1, parsed.telemetryModeLocation)
        assertEquals(2, parsed.telemetryModeEnvironment)
    }

    @Test
    fun `v3 channel message decodes signal path and text`() {
        val frame = byteArrayOf(17, 10, 0, 0, 3, 2, 0) + uint32(1_700_000_000) + "hello mesh".encodeToByteArray()

        val parsed = assertIs<MeshCoreFrame.ChannelMessage>(MeshCoreCompanionProtocol.parseFrame(frame))

        assertEquals(3, parsed.channelIndex)
        assertEquals(2.5f, parsed.snrDb)
        assertEquals(MeshCorePathMode.FLOOD, parsed.path.mode)
        assertEquals(2, parsed.path.hopCount)
        assertEquals("hello mesh", parsed.text)
    }

    @Test
    fun `v3 direct message recognizes direct path and signed text`() {
        val prefix = byteArrayOf(1, 2, 3, 4, 5, 6)
        val signature = byteArrayOf(9, 8, 7, 6)
        val frame =
            byteArrayOf(16, -8, 0, 0) +
                prefix +
                byteArrayOf(-1, 2) +
                uint32(1234) +
                signature +
                "signed".encodeToByteArray()

        val parsed = assertIs<MeshCoreFrame.DirectMessage>(MeshCoreCompanionProtocol.parseFrame(frame))

        assertContentEquals(prefix, parsed.publicKeyPrefix)
        assertEquals(MeshCorePathMode.DIRECT, parsed.path.mode)
        assertContentEquals(signature, parsed.signature)
        assertEquals(-2f, parsed.snrDb)
        assertEquals("signed", parsed.text)
    }

    @Test
    fun `battery response accepts legacy and current storage fields`() {
        val legacy =
            assertIs<MeshCoreFrame.BatteryAndStorage>(MeshCoreCompanionProtocol.parseFrame(byteArrayOf(12, 100, 15)))
        val current =
            assertIs<MeshCoreFrame.BatteryAndStorage>(
                MeshCoreCompanionProtocol.parseFrame(byteArrayOf(12, 100, 15) + uint32(20) + uint32(100)),
            )

        assertEquals(3940, legacy.millivolts)
        assertNull(legacy.usedKb)
        assertEquals(20, current.usedKb)
        assertEquals(100, current.totalKb)
    }

    @Test
    fun `unknown response remains available without interpreting payload`() {
        val parsed = assertIs<MeshCoreFrame.Unknown>(MeshCoreCompanionProtocol.parseFrame(byteArrayOf(0x7F, 1, 2)))

        assertEquals(0x7F, parsed.code)
        assertContentEquals(byteArrayOf(1, 2), parsed.payload)
    }
}

private fun uint32(value: Long): ByteArray = ByteArray(4) { index -> (value shr (index * 8)).toByte() }

private fun int32(value: Int): ByteArray = ByteArray(4) { index -> (value shr (index * 8)).toByte() }
