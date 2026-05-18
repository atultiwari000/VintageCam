package com.vintagecam.camera.filter

import android.graphics.Bitmap
import com.vintagecam.camera.capture.CapturePostProcessor
import com.vintagecam.profiles.CameraProfile

class ParameterCameraFilter(
    override val profileId: String,
    private val postProcessor: CapturePostProcessor,
) : CameraFilter {
    override fun apply(bitmap: Bitmap, profile: CameraProfile, timestamp: Long): Bitmap {
        return postProcessor.apply(bitmap, profile, timestamp)
    }
}
