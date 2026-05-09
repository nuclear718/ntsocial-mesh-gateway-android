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
package com.ntsocial.meshlink.core.model.ntsocial

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NtsocialDefaultChannelTest {

    @Test
    fun `built-in url decodes canonical NTsocial channel`() {
        val channelSet = NtsocialDefaultChannel.channelSet
        val channel = channelSet.settings.single()

        channel.name shouldBe NtsocialDefaultChannel.CHANNEL_NAME
        channel.psk.size shouldBe 32
        channel.uplink_enabled shouldBe true
        channel.downlink_enabled shouldBe true
        (channelSet.lora_config != null) shouldBe true
    }
}
