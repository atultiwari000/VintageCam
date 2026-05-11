package com.vintagecam.camera

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.vintagecam.camera.capture.CaptureEvent
import com.vintagecam.profiles.CameraProfile
import kotlinx.coroutines.flow.Flow

interface CameraEngine {
    fun applyProfile(profile: CameraProfile)
    fun startPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)
    fun stopPreview()
    fun capturePhoto(profile: CameraProfile): Flow<CaptureEvent>
    fun switchCamera()
    fun setZoom(scale: Float)
}
