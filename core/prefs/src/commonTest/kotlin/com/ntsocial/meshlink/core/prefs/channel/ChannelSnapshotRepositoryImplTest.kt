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
package com.ntsocial.meshlink.core.prefs.channel

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ntsocial.meshlink.core.prefs.InMemoryPreferencesDataStore
import com.ntsocial.meshlink.core.repository.ChannelProtectionSnapshot
import com.ntsocial.meshlink.core.repository.ChannelSnapshotRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeHex
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelSnapshotRepositoryImplTest {
    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var repository: ChannelSnapshotRepository

    @BeforeTest
    fun setup() {
        dataStore = InMemoryPreferencesDataStore()
        repository = ChannelSnapshotRepositoryImpl(dataStore)
    }

    @Test fun `upgrade default has no protected snapshot`() = runTest { assertNull(repository.get(DEVICE_A)) }

    @Test
    fun `snapshot round trips channel secrets LoRa and capacity`() = runTest {
        val snapshot = snapshot("Primary A", "00112233445566778899AABBCCDDEEFF")

        repository.save(DEVICE_A, snapshot)

        assertEquals(snapshot, repository.get(DEVICE_A))
    }

    @Test
    fun `snapshot string representation redacts channel content`() {
        val secretName = "Secret channel"
        val secretPsk = "00112233445566778899AABBCCDDEEFF"
        val description = snapshot(secretName, secretPsk).toString()

        assertFalse(secretName in description)
        assertFalse(secretPsk in description)
        assertTrue("channelCount=1" in description)
    }

    @Test
    fun `snapshots are isolated by stable device identity`() = runTest {
        val snapshotA = snapshot("Primary A", "01")
        val snapshotB = snapshot("Primary B", "02")

        repository.save(DEVICE_A, snapshotA)
        repository.save(DEVICE_B, snapshotB)

        assertEquals(snapshotA, repository.get(DEVICE_A))
        assertEquals(snapshotB, repository.get(DEVICE_B))
    }

    @Test
    fun `saving a replacement is atomic from the repository contract`() = runTest {
        val original = snapshot("Original", "01", maxChannels = 8)
        val replacement = snapshot("Replacement", "02", maxChannels = 6)
        repository.save(DEVICE_A, original)

        repository.save(DEVICE_A, replacement)

        assertEquals(replacement, repository.get(DEVICE_A))
        val stored = dataStore.data.first().asMap()
        assertEquals(2, stored.size)
        assertTrue(stored.values.contains(6))
    }

    @Test
    fun `clearing one radio leaves another radio protected`() = runTest {
        val snapshotA = snapshot("Primary A", "01")
        val snapshotB = snapshot("Primary B", "02")
        repository.save(DEVICE_A, snapshotA)
        repository.save(DEVICE_B, snapshotB)

        repository.clear(DEVICE_A)

        assertNull(repository.get(DEVICE_A))
        assertEquals(snapshotB, repository.get(DEVICE_B))
    }

    @Test
    fun `preference keys contain only a domain separated identity hash`() = runTest {
        repository.save(DEVICE_A, snapshot("Primary A", "01"))

        val keyNames = dataStore.data.first().asMap().keys.map { key -> key.name }
        assertTrue(keyNames.all { key -> key.startsWith("channel-protection-") })
        assertTrue(keyNames.all { key -> DEVICE_A !in key })
        assertTrue(keyNames.all { key -> key.substringAfterLast('-').length == SHA_256_HEX_LENGTH })
    }

    @Test
    fun `corrupt payload fails closed without deleting unrelated preferences`() = runTest {
        repository.save(DEVICE_A, snapshot("Primary A", "01"))
        val unrelatedKey = stringPreferencesKey("unrelated")
        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                this[unrelatedKey] = "keep"
                val payloadKeyName =
                    asMap().keys.single { key -> key.name.startsWith("channel-protection-set-v1-") }.name
                this[stringPreferencesKey(payloadKeyName)] = "not-base64!"
            }
        }

        assertNull(repository.get(DEVICE_A))
        assertEquals("keep", dataStore.data.first()[unrelatedKey])
    }

    @Test
    fun `missing capacity fails closed`() = runTest {
        repository.save(DEVICE_A, snapshot("Primary A", "01"))
        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                val maxKeyName = asMap().keys.single { key -> key.name.startsWith("channel-protection-max-v1-") }.name
                remove(intPreferencesKey(maxKeyName))
            }
        }

        assertNull(repository.get(DEVICE_A))
    }

    @Test
    fun `blank stable identity cannot enable protection`() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.save("  ", snapshot("Primary A", "01")) }
        assertNull(repository.get("  "))

        repository.clear("  ")
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `snapshot model rejects missing primary and impossible capacity`() {
        assertFailsWith<IllegalArgumentException> {
            ChannelProtectionSnapshot(maxChannels = 8, channelSet = ChannelSet())
        }
        assertFailsWith<IllegalArgumentException> {
            ChannelProtectionSnapshot(maxChannels = 8, channelSet = ChannelSet(settings = listOf(ChannelSettings())))
        }
        assertFailsWith<IllegalArgumentException> {
            ChannelProtectionSnapshot(
                maxChannels = 1,
                channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "A"), ChannelSettings(name = "B"))),
            )
        }
        assertFalse(runCatching { ChannelProtectionSnapshot(maxChannels = 0, channelSet = ChannelSet()) }.isSuccess)
    }

    private fun snapshot(name: String, pskHex: String, maxChannels: Int = 8): ChannelProtectionSnapshot =
        ChannelProtectionSnapshot(
            maxChannels = maxChannels,
            channelSet =
            ChannelSet(
                settings = listOf(ChannelSettings(name = name, psk = pskHex.decodeHex())),
                lora_config = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.TW),
            ),
        )

    private companion object {
        const val DEVICE_A = "stable-device-A"
        const val DEVICE_B = "stable-device-B"
        const val SHA_256_HEX_LENGTH = 64
    }
}
