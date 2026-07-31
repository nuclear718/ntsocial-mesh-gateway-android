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

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

private val QR_DECODE_HINTS: Map<DecodeHintType, Any> =
    mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)

/** Creates a CameraX analyzer that decodes QR codes locally with ZXing. */
internal fun createBarcodeAnalyzer(onResult: (String) -> Unit): ImageAnalysis.Analyzer {
    val reader = createQrCodeReader()

    return ImageAnalysis.Analyzer { imageProxy ->
        try {
            val result =
                decodeQrCode(
                    reader = reader,
                    luminance = imageProxy.copyLuminancePlane(),
                    width = imageProxy.width,
                    height = imageProxy.height,
                )
            result.text?.let(onResult)
        } catch (_: ReaderException) {
            // A frame without a decodable barcode is the normal scanning state.
        } catch (_: IllegalArgumentException) {
            // Skip malformed camera frames without interrupting the scanner session.
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }
}

internal fun createQrCodeReader(): MultiFormatReader = MultiFormatReader().apply { setHints(QR_DECODE_HINTS) }

internal fun decodeQrCode(reader: MultiFormatReader, luminance: ByteArray, width: Int, height: Int): Result {
    val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
    return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
}

/** Copies CameraX's padded Y plane into the contiguous luminance buffer expected by ZXing. */
private fun ImageProxy.copyLuminancePlane(): ByteArray {
    val plane = planes.first()
    return copyLuminancePlaneBytes(
        buffer = plane.buffer,
        width = width,
        height = height,
        rowStride = plane.rowStride,
        pixelStride = plane.pixelStride,
    )
}

/** Normalizes a potentially padded/interleaved CameraX luminance plane into tightly packed rows. */
internal fun copyLuminancePlaneBytes(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
): ByteArray {
    require(width > 0 && height > 0) { "Camera image dimensions must be positive" }
    require(rowStride > 0 && pixelStride > 0) { "Camera plane strides must be positive" }

    val source = buffer.duplicate()
    val firstByte = source.position()
    val lastByte = firstByte + (height - 1) * rowStride + (width - 1) * pixelStride
    require(lastByte < source.limit()) { "Camera luminance plane is smaller than the image dimensions" }

    return ByteArray(width * height).also { output ->
        for (row in 0 until height) {
            val sourceRow = firstByte + row * rowStride
            val outputRow = row * width
            for (column in 0 until width) {
                output[outputRow + column] = source.get(sourceRow + column * pixelStride)
            }
        }
    }
}
