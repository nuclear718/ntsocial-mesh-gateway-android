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
package com.ntsocial.meshlink.core.data.repository

import com.ntsocial.meshlink.core.database.DatabaseProvider
import com.ntsocial.meshlink.core.database.MeshtasticDatabase
import com.ntsocial.meshlink.core.database.getInMemoryDatabaseBuilder
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageIdentity
import com.ntsocial.meshlink.core.testing.FakeDatabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NtsocialGatewayPacketRepositoryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var databaseProvider: FakeDatabaseProvider
    private lateinit var repository: PacketRepositoryImpl

    @BeforeTest
    fun setUp() {
        databaseProvider = FakeDatabaseProvider()
        repository =
            PacketRepositoryImpl(
                databaseProvider,
                CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher),
            )
    }

    @AfterTest
    fun tearDown() {
        databaseProvider.close()
    }

    @Test
    fun `captured rows retain identity and legacy broadcasts remain best effort`() = runTest(dispatcher) {
        val packet = DataPacket(DataPacket.ID_BROADCAST, 1, "native").apply { id = 9 }
        val identity =
            NtsocialGatewayMessageIdentity(
                sourceChannelId = "meshtastic:channel",
                sourceMessageId = "0123456789ABCDEF0123456789ABCDEF",
            )

        repository.savePacket(1, "1${DataPacket.ID_BROADCAST}", packet, 100L, gatewayIdentity = identity)
        repository.savePacket(1, "1${DataPacket.ID_BROADCAST}", packet.copy(id = 10), 101L)

        val page =
            repository.getGatewayMessageChanges(
                after = 0,
                limit = 10,
                legacyBroadcastContactKeys = listOf("1${DataPacket.ID_BROADCAST}"),
            )
        assertEquals(2, page.size)
        assertEquals(identity, page.first().identity)
        assertEquals(100L, page.first().receivedAtMillis)
        assertEquals(null, page.last().identity)
        assertEquals(page.first().changeSeq, repository.getGatewayMessageChangeSeq().first())
        assertEquals(
            page.last().changeSeq,
            repository.getGatewayMessageChangeSeq(listOf("1${DataPacket.ID_BROADCAST}")).first(),
        )
        assertEquals(
            page.last().changeSeq,
            repository.getGatewayHistoryState(listOf("1${DataPacket.ID_BROADCAST}")).first().messageChangeSeq,
        )
        assertTrue(repository.getGatewayMessageChanges(page.last().changeSeq, 10).isEmpty())
        assertEquals(
            listOf(identity),
            repository.getGatewayStableMessageChanges(after = 0, limit = 10).map { it.identity },
        )
    }

    @Test
    fun `duplicate source message ids remain insertable for replay dedupe`() = runTest(dispatcher) {
        val identity =
            NtsocialGatewayMessageIdentity(
                sourceChannelId = "meshtastic:channel",
                sourceMessageId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )
        repository.savePacket(
            1,
            "1${DataPacket.ID_BROADCAST}",
            DataPacket(DataPacket.ID_BROADCAST, 1, "live").apply { id = 99 },
            100L,
            gatewayIdentity = identity,
        )
        repository.savePacket(
            1,
            "1${DataPacket.ID_BROADCAST}",
            DataPacket(DataPacket.ID_BROADCAST, 1, "replay").apply { id = 99 },
            101L,
            gatewayIdentity = identity,
        )

        val page = repository.getGatewayStableMessageChanges(after = 0, limit = 10)

        assertEquals(2, page.size)
        assertTrue(page.all { it.identity == identity })
    }

    @Test
    fun `source message id is authoritative uppercase 16 byte digest`() {
        val channel = NtsocialGatewayIdentity.channel(Channel(settings = ChannelSettings(id = 7, name = "ops")))
        val packet =
            DataPacket(DataPacket.ID_BROADCAST, 2, "hello").apply {
                from = "!12345678"
                id = 0xFFFF_FF01.toInt()
            }

        val identity = requireNotNull(NtsocialGatewayIdentity.nativeBroadcastText(channel, packet))
        val sameAfterReorder =
            requireNotNull(NtsocialGatewayIdentity.nativeBroadcastText(channel, packet.copy(channel = 5)))

        assertEquals(identity.sourceMessageId, sameAfterReorder.sourceMessageId)
        assertEquals(32, identity.sourceMessageId.length)
        assertTrue(identity.sourceMessageId.all { it in '0'..'9' || it in 'A'..'F' })
        assertNull(NtsocialGatewayIdentity.nativeBroadcastText(channel, DataPacket("!87654321", 0, "private")))
        assertNull(NtsocialGatewayIdentity.nativeBroadcastText(channel, packet.copy(from = DataPacket.ID_LOCAL)))
    }

    @Test
    fun `clear all rotates durable history epoch`() = runTest(dispatcher) {
        val key = "0${DataPacket.ID_BROADCAST}"
        repository.savePacket(
            1,
            key,
            DataPacket(DataPacket.ID_BROADCAST, 0, "before").apply {
                from = "!00000001"
                id = 1
            },
            100L,
        )
        val before = repository.getGatewayHistoryState(listOf(key)).first()

        repository.clearPacketDB()

        val after = repository.getGatewayHistoryState(listOf(key)).first()
        assertNotEquals(before.historyEpoch, after.historyEpoch)
        assertEquals(0L, after.messageChangeSeq)
    }

    @Test
    fun `switching history databases changes epoch even when new sequence is lower`() = runTest(dispatcher) {
        val provider = SwitchingDatabaseProvider()
        val switchingRepository =
            PacketRepositoryImpl(
                provider,
                CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher),
            )
        val key = "0${DataPacket.ID_BROADCAST}"
        repeat(3) { index ->
            switchingRepository.savePacket(
                1,
                key,
                DataPacket(DataPacket.ID_BROADCAST, 0, "a-$index").apply {
                    from = "!00000001"
                    id = index + 1
                },
                index.toLong(),
            )
        }
        val firstDomain = switchingRepository.getGatewayHistoryState(listOf(key)).first()

        provider.switchToFreshDatabase()
        switchingRepository.savePacket(
            1,
            key,
            DataPacket(DataPacket.ID_BROADCAST, 0, "b").apply {
                from = "!00000002"
                id = 4
            },
            4L,
        )
        val secondDomain = switchingRepository.getGatewayHistoryState(listOf(key)).first()

        assertNotEquals(firstDomain.historyEpoch, secondDomain.historyEpoch)
        assertTrue(secondDomain.messageChangeSeq < firstDomain.messageChangeSeq)
        provider.close()
    }

    private class SwitchingDatabaseProvider : DatabaseProvider {
        private val databases = mutableListOf(getInMemoryDatabaseBuilder().build())
        private val mutableCurrentDb = MutableStateFlow(databases.single())
        override val currentDb: StateFlow<MeshtasticDatabase> = mutableCurrentDb

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T = block(mutableCurrentDb.value)

        fun switchToFreshDatabase() {
            mutableCurrentDb.value = getInMemoryDatabaseBuilder().build().also(databases::add)
        }

        fun close() {
            databases.forEach(MeshtasticDatabase::close)
        }
    }
}
