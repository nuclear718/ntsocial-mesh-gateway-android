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
package com.ntsocial.meshlink.core.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class BarcodeAnalyzerTest {

    @Test
    fun decodesDenseMeshtasticUrlFromPaddedHdCameraPlane() {
        val channelUrl =
            MESHTASTIC_CHANNEL_PREFIX +
                buildString {
                    repeat(CHANNEL_FRAGMENT_LENGTH) { index ->
                        append(BASE64_URL_ALPHABET[index % BASE64_URL_ALPHABET.length])
                    }
                }
        assertEquals(EXPECTED_CHANNEL_URL_LENGTH, channelUrl.length)

        val qrMatrix = QRCodeWriter().encode(channelUrl, BarcodeFormat.QR_CODE, QR_CODE_DIMENSION, QR_CODE_DIMENSION)
        val packedFrame = ByteArray(FRAME_WIDTH * FRAME_HEIGHT) { WHITE }
        val left = (FRAME_WIDTH - QR_CODE_DIMENSION) / 2
        val top = (FRAME_HEIGHT - QR_CODE_DIMENSION) / 2
        for (y in 0 until QR_CODE_DIMENSION) {
            for (x in 0 until QR_CODE_DIMENSION) {
                packedFrame[(top + y) * FRAME_WIDTH + left + x] =
                    if (qrMatrix[x, y]) {
                        BLACK
                    } else {
                        WHITE
                    }
            }
        }

        val rowStride = FRAME_WIDTH + ROW_PADDING
        val paddedPlane = ByteArray(BUFFER_PREFIX + rowStride * FRAME_HEIGHT) { PADDING }
        for (row in 0 until FRAME_HEIGHT) {
            packedFrame.copyInto(
                destination = paddedPlane,
                destinationOffset = BUFFER_PREFIX + row * rowStride,
                startIndex = row * FRAME_WIDTH,
                endIndex = (row + 1) * FRAME_WIDTH,
            )
        }

        val paddedBuffer = ByteBuffer.wrap(paddedPlane)
        paddedBuffer.position(BUFFER_PREFIX)
        val normalized =
            copyLuminancePlaneBytes(
                buffer = paddedBuffer,
                width = FRAME_WIDTH,
                height = FRAME_HEIGHT,
                rowStride = rowStride,
                pixelStride = 1,
            )
        val decoded = decodeQrCode(createQrCodeReader(), normalized, FRAME_WIDTH, FRAME_HEIGHT)

        assertEquals(channelUrl, decoded.text)
    }

    private companion object {
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 960
        const val QR_CODE_DIMENSION = 420
        const val ROW_PADDING = 17
        const val BUFFER_PREFIX = 5
        const val CHANNEL_FRAGMENT_LENGTH = 552
        const val EXPECTED_CHANNEL_URL_LENGTH = 587
        const val MESHTASTIC_CHANNEL_PREFIX = "https://meshtastic.org/e/?add=true#"
        const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        const val BLACK: Byte = 0
        const val WHITE: Byte = -1
        const val PADDING: Byte = 0x7F
    }
}
