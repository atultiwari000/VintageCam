package com.vintagecam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vintagecam.camera.capture.CaptureEvent
import com.vintagecam.camera.capture.CapturePostProcessor
import com.vintagecam.camera.capture.GallerySaver
import com.vintagecam.profiles.CameraProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraXEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraEngine {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var currentProfile: CameraProfile? = null

    override fun applyProfile(profile: CameraProfile) {
        currentProfile = profile
    }

    override fun startPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        try {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(surfaceProvider) }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        currentCameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CameraXEngine", "Failed to start preview", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to get camera provider", e)
        }
    }

    override fun stopPreview() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to stop preview", e)
        }
    }

    override fun capturePhoto(profile: CameraProfile): Flow<CaptureEvent> = flow {
        emit(CaptureEvent.Processing)
        val capture = imageCapture ?: throw IllegalStateException("Camera not started")

        val imageProxy = suspendCancellableCoroutine<ImageProxy> { cont ->
            try {
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            cont.resume(image) {}
                        }

                        override fun onError(exception: ImageCaptureException) {
                            cont.resumeWithException(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

        val bitmap = imageProxy.toBitmap()
        imageProxy.close()

        // Apply CPU post-processing
        val postProcessor = CapturePostProcessor()
        val processed = postProcessor.apply(bitmap, profile, System.currentTimeMillis())

        // Save to gallery
        val gallerySaver = GallerySaver(context)
        val uri = try {
            gallerySaver.save(processed, profile, System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to save to gallery", e)
            Uri.EMPTY
        }

        emit(CaptureEvent.Success(processed, uri, System.currentTimeMillis()))
    }.flowOn(Dispatchers.IO)

    override fun switchCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    override fun setZoom(scale: Float) {}

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
