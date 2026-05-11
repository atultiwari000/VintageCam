package com.vintagecam.camera

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class CameraXEngineImpl @Inject constructor() : CameraEngine {
    override fun capturePhoto(): Flow<Bitmap> = flow { /* TODO */ }
}
