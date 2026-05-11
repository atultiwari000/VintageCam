package com.vintagecam.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.annotation.SuppressLint
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vintagecam.camera.pipeline.CpuFilterApplier
import com.vintagecam.camera.capture.CaptureEvent
import com.vintagecam.camera.capture.CapturePostProcessor
import com.vintagecam.camera.capture.GallerySaver
import com.vintagecam.camera.capture.toBitmap
import com.vintagecam.profiles.CameraProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraXEngineImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cpuFilterApplier: CpuFilterApplier,
    private val capturePostProcessor: CapturePostProcessor,
) : CameraEngine {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var activeLensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var lifecycleOwner: LifecycleOwner? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var activeProfile: CameraProfile? = null

    override fun startPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        this.lifecycleOwner = lifecycleOwner
        this.surfaceProvider = surfaceProvider
        bindUseCases()
    }

    override fun stopPreview() {
        cameraProvider?.unbindAll()
        camera = null
        previewUseCase = null
        imageCaptureUseCase = null
    }

    override fun capturePhoto(profile: CameraProfile): Flow<CaptureEvent> = flow {
        val imageCapture = imageCaptureUseCase
            ?: throw IllegalStateException("ImageCapture is not initialized. Call startPreview() first.")

        delay(profile.captureLatencyMs)
        emit(CaptureEvent.Processing)

        val image = suspendCancellableCoroutine<ImageProxy> { continuation ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        continuation.resume(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }

        try {
            val bitmap = image.toBitmap()
            val shaderAdjusted = cpuFilterApplier.applyProfile(bitmap, profile)
            val processed = capturePostProcessor.apply(shaderAdjusted, profile, System.currentTimeMillis())
            val uri = GallerySaver(appContext).save(processed, profile, System.currentTimeMillis())
            emit(CaptureEvent.Success(processed, uri, System.currentTimeMillis()))
        } finally {
            image.close()
        }
    }

    override fun setZoom(scale: Float) {
        val clamped = scale.coerceIn(1f, camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f)
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    override fun switchCamera() {
        activeLensFacing = if (activeLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases()
    }

    override fun applyProfile(profile: CameraProfile) {
        activeProfile = profile
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindUseCases() {
        val owner = lifecycleOwner ?: return
        val surface = surfaceProvider ?: return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val previewBuilder = Preview.Builder()
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON,
                    )
                val preview = previewBuilder.build().apply {
                    setSurfaceProvider(surface)
                }

                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                Camera2Interop.Extender(captureBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON,
                    )
                val imageCapture = captureBuilder.build()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(activeLensFacing)
                    .build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, selector, preview, imageCapture)
                previewUseCase = preview
                imageCaptureUseCase = imageCapture
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

}
