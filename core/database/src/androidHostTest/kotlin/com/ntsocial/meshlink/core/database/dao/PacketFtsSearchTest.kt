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
package com.ntsocial.meshlink.core.database.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.database.MeshtasticDatabase
import com.ntsocial.meshlink.core.database.MeshtasticDatabaseConstructor
import com.ntsocial.meshlink.core.database.entity.MyNodeEntity
import com.ntsocial.meshlink.core.database.entity.Packet
import com.ntsocial.meshlink.core.model.DataPacket
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.proto.PortNum
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PacketFtsSearchTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var packetDao: PacketDao

    private val myNodeNum = 42424242

    @Before
    fun createDb(): Unit = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(
                context = context,
                factory = { MeshtasticDatabaseConstructor.initialize() },
            )
                .setDriver(BundledSQLiteDriver())
                .build()
        database
            .nodeInfoDao()
            .setMyNodeInfo(
                MyNodeEntity(
                    myNodeNum = myNodeNum,
                    model = null,
                    firmwareVersion = null,
                    couldUpdate = false,
                    shouldUpdate = false,
                    currentPacketId = 1L,
                    messageTimeoutMsec = 5 * 60 * 1000,
                    minAppVersion = 1,
                    maxChannels = 8,
                    hasWifi = false,
                ),
            )
        packetDao = database.packetDao()
    }

    @After
    fun closeDb() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun searchMessages_matchesIndexedText_ignoresNonMatches() = runTest {
        insertTextPacket(contactKey = CONTACT, text = "the quick brown fox", messageText = "the quick brown fox")

        assertEquals(1, packetDao.searchMessages("brown").size)
        assertTrue(packetDao.searchMessages("zebra").isEmpty())
    }

    @Test
    fun searchMessagesInConversation_scopesToContact() = runTest {
        insertTextPacket(contactKey = CONTACT, text = "shared keyword here", messageText = "shared keyword here")
        insertTextPacket(contactKey = OTHER_CONTACT, text = "shared keyword here", messageText = "shared keyword here")

        assertEquals(2, packetDao.searchMessages("keyword").size)
        assertEquals(1, packetDao.searchMessagesInConversation("keyword", CONTACT).size)
    }

    @Test
    fun backfillMessageTexts_makesHistoricalMessagesSearchable() = runTest {
        insertTextPacket(contactKey = CONTACT, text = "historical needle", messageText = "")

        assertTrue(packetDao.searchMessages("needle").isEmpty())
        assertEquals(1, packetDao.countPacketsNeedingBackfill())

        val updated = packetDao.backfillMessageTexts()
        packetDao.rebuildFtsIndex()

        assertEquals(1, updated)
        assertEquals(1, packetDao.searchMessages("needle").size)
        assertEquals(0, packetDao.countPacketsNeedingBackfill())
    }

    private suspend fun insertTextPacket(contactKey: String, text: String, messageText: String) {
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = contactKey,
                received_time = nowMillis,
                read = false,
                data = DataPacket(to = DataPacket.ID_BROADCAST, channel = 0, text = text),
                messageText = messageText,
            ),
        )
    }

    companion object {
        private const val CONTACT = "0!aaaa1111"
        private const val OTHER_CONTACT = "0!bbbb2222"
    }
}
