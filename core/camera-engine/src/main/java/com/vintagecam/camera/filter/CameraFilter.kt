package com.vintagecam.camera.filter

import android.graphics.Bitmap
import com.vintagecam.profiles.CameraProfile

interface CameraFilter {
    val profileId: String
    fun apply(bitmap: Bitmap, profile: CameraProfile, timestamp: Long): Bitmap
}
