package com.vintagecam.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.vintagecam.profiles.CameraProfile

data class CaptureResult(
    val bitmap: Bitmap,
    val uri: Uri,
    val capturedAtMillis: Long,
)

data class RawCaptureResult(
    val bitmap: Bitmap,
    val capturedAtMillis: Long,
    val mergeFrames: List<Bitmap> = emptyList(),
)

interface CameraEngine {
    fun applyProfile(profile: CameraProfile)
    suspend fun startPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)
    fun stopPreview()
    suspend fun captureRawPhoto(profile: CameraProfile, capturedAtMillis: Long = System.currentTimeMillis()): RawCaptureResult
    suspend fun processPhoto(bitmap: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap
    suspend fun processPhoto(raw: RawCaptureResult, profile: CameraProfile): Bitmap
    suspend fun capturePhoto(profile: CameraProfile): CaptureResult
    fun switchCamera()
    fun setFlashEnabled(enabled: Boolean)
    fun focusAt(previewView: PreviewView, x: Float, y: Float)
    fun setZoom(scale: Float)
}
