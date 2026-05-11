package com.vintagecam.camera.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

internal fun ImageProxy.toBitmap(): Bitmap {
    return when (format) {
        ImageFormat.YUV_420_888 -> yuv420888ToBitmap()
        else -> throw IllegalArgumentException("Unsupported image format: $format")
    }
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
