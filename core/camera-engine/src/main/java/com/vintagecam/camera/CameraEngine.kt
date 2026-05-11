package com.vintagecam.camera

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

interface CameraEngine {
    fun capturePhoto(): Flow<Bitmap>
}
