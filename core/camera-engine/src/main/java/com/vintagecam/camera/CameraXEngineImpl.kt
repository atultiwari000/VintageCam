package com.vintagecam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
import com.vintagecam.profiles.CameraProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@ExperimentalCamera2Interop
class CameraXEngineImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cpuFilterApplier: CpuFilterApplier,
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

    override fun capturePhoto(): Flow<Bitmap> = callbackFlow {
        val imageCapture = imageCaptureUseCase
        if (imageCapture == null) {
            close(IllegalStateException("ImageCapture is not initialized. Call startPreview() first."))
            return@callbackFlow
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        val profiledBitmap = activeProfile?.let { profile ->
                            cpuFilterApplier.applyProfile(bitmap, profile)
                        } ?: bitmap
                        trySend(profiledBitmap)
                        close()
                    } catch (t: Throwable) {
                        close(t)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    close(exception)
                }
            },
        )

        awaitClose { }
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

    @ExperimentalCamera2Interop
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

    // v0.1 CPU fallback: convert YUV_420_888 frames to RGB bitmap for software processing.
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        return when (image.format) {
            ImageFormat.YUV_420_888 -> yuv420888ToBitmap(image)
            else -> throw IllegalArgumentException("Unsupported image format: ${image.format}")
        }
    }

    private fun yuv420888ToBitmap(image: ImageProxy): Bitmap {
        val nv21 = yuv420888ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 95, out)
        val jpegBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("Failed to decode bitmap from camera frame")
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride
        val width = image.width
        val height = image.height
        var outputPos = ySize

        val vBytes = ByteArray(vSize)
        val uBytes = ByteArray(uSize)
        vBuffer.get(vBytes)
        uBuffer.get(uBytes)

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val chromaIndex = row * chromaRowStride + col * chromaPixelStride
                nv21[outputPos++] = vBytes[chromaIndex]
                nv21[outputPos++] = uBytes[chromaIndex]
            }
        }

        return nv21
    }
}
