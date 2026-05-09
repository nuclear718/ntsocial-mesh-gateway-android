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

import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.model.util.toChannelSet
import org.meshtastic.proto.ChannelSet

/** Canonical public NTsocial Meshtastic channel bundled with NTsocial MeshLink. */
object NtsocialDefaultChannel {
    const val CHANNEL_NAME = "NTsocial"

    const val CHANNEL_URL =
        "https://meshtastic.org/e/#" +
            "CjQSIEev7B-RWZCrH1mmuOMxuIZr5xasC38s0KnqqSPMA1BfGghOVHNvY2lhbCgBMAE6AgggEhoIARAEGPoBIAQoBTgIQAdIAVAQaAF1AAhmRA"

    val channelSet: ChannelSet by lazy { CommonUri.parse(CHANNEL_URL).toChannelSet() }
}
