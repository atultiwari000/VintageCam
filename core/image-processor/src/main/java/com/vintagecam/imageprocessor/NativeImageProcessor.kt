package com.vintagecam.imageprocessor

import android.graphics.Bitmap

/**
 * JNI bridge to the native C++ image processing library (libvintagecam_processor.so).
 *
 * All methods call into native code synchronously via JNI.
 * The native library performs zero-copy processing using OpenCV.
 *
 * Thread safety: Native methods may be called from any thread.
 * Bitmap objects must have mutable ARGB_8888 config for in-place operations.
 */
class NativeImageProcessor {

    companion object {
        private var loaded: Boolean = false

        init {
            try {
                System.loadLibrary("vintagecam_processor")
                loaded = true
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("NativeImageProcessor", "Failed to load native library", e)
                loaded = false
            }
        }
    }

    /** Returns true if the native library loaded successfully. */
    fun isAvailable(): Boolean = NativeImageProcessor.loaded

    /**
     * Process a YUV_420_888 camera frame directly (no JPEG round-trip).
     *
     * @param y              Luma plane bytes (width × height)
     * @param u              U chroma plane bytes
     * @param v              V chroma plane bytes
     * @param width          Frame width in pixels
     * @param height         Frame height in pixels
     * @param yStride        Row stride of the Y plane
     * @param uStride        Row stride of the U plane
     * @param vStride        Row stride of the V plane
     * @param uvPixelStride  Pixel stride of chroma planes (1 for planar, 2 for semi-planar)
     * @param presetId       Filter preset identifier (e.g. "vhs_1985")
     * @param timestamp      Capture timestamp for date stamps and temporal effects
     * @param outBitmap      Pre-allocated mutable ARGB_8888 Bitmap to receive output
     * @return true if processing succeeded, false if fallback is needed
     */
    external fun processYuvFrame(
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
        uStride: Int,
        vStride: Int,
        uvPixelStride: Int,
        presetId: String,
        timestamp: Long,
        outBitmap: Bitmap,
    ): Boolean

    /**
     * Process a pre-decoded Bitmap in-place using the native pipeline.
     *
     * @param bitmap     Mutable ARGB_8888 Bitmap (modified in-place)
     * @param presetId   Filter preset identifier
     * @param timestamp  Capture timestamp
     * @return true if processing succeeded
     */
    external fun processBitmap(
        bitmap: Bitmap,
        presetId: String,
        timestamp: Long,
    ): Boolean

    /**
     * Align and merge a burst of native cv::Mat frames.
     *
     * @param matAddrs       Array of native cv::Mat addresses
     * @param count          Number of frames
     * @param alignmentMode  0 = ECC, 1 = OPTICAL_FLOW, 2 = NONE
     * @return Native address of merged cv::Mat (caller owns, release via releaseNativeMat)
     */
    external fun mergeBurst(
        matAddrs: LongArray,
        count: Int,
        alignmentMode: Int,
    ): Long

    /**
     * Generate a procedural grain texture.
     *
     * @param width      Output texture width
     * @param height     Output texture height
     * @param intensity  0.0-1.0 grain strength
     * @param seed       Random seed for temporal variation
     * @param grainSize  0=fine, 1=medium, 2=coarse
     * @return Native cv::Mat address (caller owns, release via releaseNativeMat)
     */
    external fun generateGrainTexture(
        width: Int,
        height: Int,
        intensity: Float,
        seed: Int,
        grainSize: Int,
    ): Long

    /**
     * Release a native cv::Mat previously returned by mergeBurst or generateGrainTexture.
     */
    external fun releaseNativeMat(matAddr: Long)
}
