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
package com.ntsocial.meshlink.app.ui

import com.ntsocial.meshlink.core.model.util.getInitials
import kotlin.test.Test
import kotlin.test.assertEquals

class UIUnitTest {
    @Test
    fun initialsGood() {
        assertEquals("KH", getInitials("Kevin Hester"))
        assertEquals("KHLC", getInitials("  Kevin Hester Lesser Cat  "))
        assertEquals("", getInitials("  "))
        assertEquals("gksv", getInitials("geeksville"))
        assertEquals("geek", getInitials("geek"))
        assertEquals("gks1", getInitials("geeks1"))
    }

    @Test
    fun ignoreEmojisWhenCreatingInitials() {
        assertEquals("TG", getInitials("The \uD83D\uDC10 Goat"))
        assertEquals("TT", getInitials("The \uD83E\uDD14Thinker"))
        assertEquals("TCH", getInitials("\uD83D\uDC4F\uD83C\uDFFFThe Clapping Hands"))
        assertEquals("山羊", getInitials("山羊\uD83D\uDC10"))
    }
}
