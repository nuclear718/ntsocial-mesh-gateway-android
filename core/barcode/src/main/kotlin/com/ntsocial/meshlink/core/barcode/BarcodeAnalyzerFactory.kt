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
package com.ntsocial.meshlink.core.barcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Creates a CameraX analyzer that decodes QR codes and barcodes locally with ZXing. */
internal fun createBarcodeAnalyzer(onResult: (String) -> Unit): ImageAnalysis.Analyzer {
    val reader = MultiFormatReader()

    return ImageAnalysis.Analyzer { imageProxy ->
        try {
            val source =
                PlanarYUVLuminanceSource(
                    imageProxy.copyLuminancePlane(),
                    imageProxy.width,
                    imageProxy.height,
                    0,
                    0,
                    imageProxy.width,
                    imageProxy.height,
                    false,
                )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
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

/** Copies CameraX's padded Y plane into the contiguous luminance buffer expected by ZXing. */
private fun ImageProxy.copyLuminancePlane(): ByteArray {
    val plane = planes.first()
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val firstByte = buffer.position()
    val lastByte = firstByte + (height - 1) * rowStride + (width - 1) * pixelStride
    require(lastByte < buffer.limit()) { "Camera luminance plane is smaller than the image dimensions" }

    return ByteArray(width * height).also { output ->
        for (row in 0 until height) {
            val sourceRow = firstByte + row * rowStride
            val outputRow = row * width
            for (column in 0 until width) {
                output[outputRow + column] = buffer.get(sourceRow + column * pixelStride)
            }
        }
    }
}
