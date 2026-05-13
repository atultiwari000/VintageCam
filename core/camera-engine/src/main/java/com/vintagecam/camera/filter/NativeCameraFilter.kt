package com.vintagecam.camera.filter

import android.graphics.Bitmap
import com.vintagecam.imageprocessor.NativeImageProcessor
import com.vintagecam.profiles.CameraProfile

/**
 * [CameraFilter] implementation that delegates to the native C++ image processor.
 *
 * Unlike the Kotlin CPU filters (which create intermediate Bitmaps per effect),
 * this filter sends the entire bitmap to native code for zero-copy processing.
 */
class NativeCameraFilter(
    override val profileId: String,
    private val nativeProcessor: NativeImageProcessor,
) : CameraFilter {

    override fun apply(bitmap: Bitmap, profile: CameraProfile, timestamp: Long): Bitmap {
        // processBitmap modifies the bitmap in-place when possible.
        // If the native call fails, return the unmodified bitmap
        // (caller should have already fallen back).
        val success = nativeProcessor.processBitmap(
            bitmap = bitmap,
            presetId = profile.id,
            timestamp = timestamp,
        )

        if (!success) {
            android.util.Log.e(
                "NativeCameraFilter",
                "Native processBitmap failed for profile=$profileId",
            )
        }

        return bitmap
    }
}
