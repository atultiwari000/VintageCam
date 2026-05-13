package com.vintagecam.camera.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Extracted YUV plane data for direct native processing.
 */
data class YuvBytes(
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val width: Int,
    val height: Int,
    val yStride: Int,
    val uStride: Int,
    val vStride: Int,
    val uvPixelStride: Int,
)

internal fun ImageProxy.toBitmap(): Bitmap {
    return when (format) {
        ImageFormat.YUV_420_888 -> yuv420888ToBitmap()
        ImageFormat.JPEG, 256 -> jpegToBitmap()
        else -> throw IllegalArgumentException("Unsupported image format: $format")
    }
}

/**
 * Extract YUV plane bytes for direct native processing (zero JPEG round-trip).
 * Returns all plane data and stride information needed by the native YUV→RGBA converter.
 */
internal fun ImageProxy.toYuvBytes(): YuvBytes {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val yBytes = ByteArray(yBuffer.remaining())
    val uBytes = ByteArray(uBuffer.remaining())
    val vBytes = ByteArray(vBuffer.remaining())

    yBuffer.get(yBytes)
    uBuffer.get(uBytes)
    vBuffer.get(vBytes)

    return YuvBytes(
        y = yBytes,
        u = uBytes,
        v = vBytes,
        width = width,
        height = height,
        yStride = planes[0].rowStride,
        uStride = planes[1].rowStride,
        vStride = planes[2].rowStride,
        uvPixelStride = planes[1].pixelStride,
    )
}

private fun ImageProxy.jpegToBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
        ?: throw IllegalStateException("Failed to decode JPEG bitmap")
}

private fun ImageProxy.yuv420888ToBitmap(): Bitmap {
    val nv21 = yuv420888ToNv21()
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val output = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 95, output)
    val jpegBytes = output.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        ?: throw IllegalStateException("Failed to decode bitmap from camera frame")
}

private fun ImageProxy.yuv420888ToNv21(): ByteArray {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)

    val chromaRowStride = planes[1].rowStride
    val chromaPixelStride = planes[1].pixelStride
    val width = this.width
    val height = this.height
    var outputPos = ySize

    val vBytes = ByteArray(vSize)
    val uBytes = ByteArray(uSize)
    vBuffer.get(vBytes)
    uBuffer.get(uBytes)

    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {
            val chromaIndex = row * chromaRowStride + col * chromaPixelStride
            nv21[outputPos++] = vBytes[chromaIndex]
            nv21[outputPos++] = uBytes[chromaIndex]
        }
    }

    return nv21
}
