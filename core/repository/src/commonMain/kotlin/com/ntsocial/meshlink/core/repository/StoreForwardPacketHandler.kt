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
package com.ntsocial.meshlink.core.repository

import com.ntsocial.meshlink.core.model.DataPacket
import org.meshtastic.proto.MeshPacket

/** Interface for handling Store & Forward (legacy) and SF++ packets. */
interface StoreForwardPacketHandler {
    /**
     * Handles a legacy Store & Forward packet.
     *
     * @param packet The received mesh packet.
     * @param dataPacket The decoded data packet.
     * @param myNodeNum The local node number.
     */
    fun handleStoreAndForward(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int)

    /**
     * Handles a Store Forward++ packet.
     *
     * @param packet The received mesh packet.
     */
    fun handleStoreForwardPlusPlus(packet: MeshPacket)
}
