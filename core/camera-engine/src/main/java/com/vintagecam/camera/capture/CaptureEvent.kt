package com.vintagecam.camera.capture

import android.graphics.Bitmap
import android.net.Uri

sealed interface CaptureEvent {
    data object Processing : CaptureEvent

    data class Success(
        val bitmap: Bitmap,
        val uri: Uri,
        val capturedAtMillis: Long,
    ) : CaptureEvent
}
