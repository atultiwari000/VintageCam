package com.vintagecam.camera

import android.graphics.Bitmap
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.vintagecam.profiles.CameraProfile
import kotlinx.coroutines.flow.Flow

interface CameraEngine {
    fun startPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)

    fun stopPreview()

    fun capturePhoto(): Flow<Bitmap>

    fun setZoom(scale: Float)

    fun switchCamera()

    fun applyProfile(profile: CameraProfile)
}
