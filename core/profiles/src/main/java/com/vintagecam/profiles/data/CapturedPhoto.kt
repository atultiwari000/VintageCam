package com.vintagecam.profiles.data

import android.graphics.Bitmap
import android.net.Uri
import com.vintagecam.profiles.CameraProfile

data class CapturedPhoto(
    val bitmap: Bitmap,
    val profile: CameraProfile,
    val timestampMillis: Long,
    val uri: Uri,
)
