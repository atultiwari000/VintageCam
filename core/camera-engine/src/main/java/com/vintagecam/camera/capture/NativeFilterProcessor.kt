package com.vintagecam.camera.capture

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.vintagecam.camera.filter.FilterFactory
import com.vintagecam.imageprocessor.NativeImageProcessor
import com.vintagecam.profiles.CameraProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native-first capture processor with automatic Kotlin fallback.
 *
 * Primary entry point for post-capture image processing. Attempts the native
 * C++ pipeline first and falls back to the existing Kotlin CPU filter path
 * if the native library is unavailable or processing fails.
 */
@Singleton
class NativeFilterProcessor @Inject constructor(
    private val nativeImageProcessor: NativeImageProcessor,
    private val fallbackFactory: FilterFactory,
) {
    /**
     * Process a captured [ImageProxy] through the native pipeline or fallback.
     *
     * @param imageProxy  Raw camera frame (YUV_420_888 or JPEG)
     * @param profile     Camera profile with filter parameters
     * @param timestamp   Capture timestamp for date stamps and temporal effects
     * @return Processed [Bitmap] ready for gallery save
     */
    fun process(
        imageProxy: ImageProxy,
        profile: CameraProfile,
        timestamp: Long,
    ): Bitmap {
        // Fast path: native YUV processing (zero JPEG round-trip)
        if (nativeImageProcessor.isAvailable() && profile.id in nativeSupportedProfiles && imageProxy.format == ImageFormat.YUV_420_888) {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val yBytes = ByteArray(yBuffer.remaining())
            val uBytes = ByteArray(uBuffer.remaining())
            val vBytes = ByteArray(vBuffer.remaining())

            yBuffer.get(yBytes)
            uBuffer.get(uBytes)
            vBuffer.get(vBytes)

            val outBitmap = Bitmap.createBitmap(
                imageProxy.width, imageProxy.height,
                Bitmap.Config.ARGB_8888,
            )

            val success = nativeImageProcessor.processYuvFrame(
                y = yBytes,
                u = uBytes,
                v = vBytes,
                width = imageProxy.width,
                height = imageProxy.height,
                yStride = imageProxy.planes[0].rowStride,
                uStride = imageProxy.planes[1].rowStride,
                vStride = imageProxy.planes[2].rowStride,
                uvPixelStride = imageProxy.planes[1].pixelStride,
                presetId = profile.id,
                timestamp = timestamp,
                outBitmap = outBitmap,
            )

            if (success) return outBitmap

            android.util.Log.w(
                "NativeFilterProcessor",
                "Native YUV processing failed for ${profile.id}, falling back",
            )
        }

        // Fallback: Kotlin CPU path (JPEG round-trip via existing filters)
        val bitmap = imageProxy.toBitmap()
        val filter = fallbackFactory.getKotlinFilter(profile.id)
        return filter.apply(bitmap, profile, timestamp)
    }

    /**
     * Process a pre-decoded bitmap. Used when the UI wants to release the
     * camera for the next shot as soon as the raw frame has been captured.
     */
    fun processBitmap(
        bitmap: Bitmap,
        profile: CameraProfile,
        timestamp: Long,
    ): Bitmap {
        if (nativeImageProcessor.isAvailable() && profile.id in nativeSupportedProfiles) {
            val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val success = nativeImageProcessor.processBitmap(
                bitmap = mutable,
                presetId = profile.id,
                timestamp = timestamp,
            )
            if (success) return mutable

            android.util.Log.w(
                "NativeFilterProcessor",
                "Native bitmap processing failed for ${profile.id}, falling back",
            )
        }

        val filter = fallbackFactory.getKotlinFilter(profile.id)
        return filter.apply(bitmap, profile, timestamp)
    }

    private companion object {
        val nativeSupportedProfiles = setOf("vhs_1985", "disposable_1998", "digicam_2003")
    }
}
