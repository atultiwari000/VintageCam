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
import com.vintagecam.camera.capture.CapturePostProcessor
import com.vintagecam.camera.capture.GallerySaver
import com.vintagecam.profiles.CameraProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
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
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundSurfaceProvider: Preview.SurfaceProvider? = null

    override fun applyProfile(profile: CameraProfile) {
        currentProfile = profile
    }

    
    
    override suspend fun startPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        boundLifecycleOwner = lifecycleOwner
        boundSurfaceProvider = surfaceProvider

        // Acquire provider and bind on the main dispatcher. Use ListenableFuture.await() helper.
        withContext(Dispatchers.Main) {
            val provider = ProcessCameraProvider.getInstance(context).await()
            cameraProvider = provider
            bindInternal(lifecycleOwner, surfaceProvider)
        }
    }
    override fun stopPreview() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to stop preview", e)
        }
    }

    
    
    override suspend fun capturePhoto(profile: CameraProfile): com.vintagecam.camera.CaptureResult {
        val capture = imageCapture ?: throw IllegalStateException("Camera not started")

        // Capture ImageProxy on main thread
        val imageProxy = withContext(kotlinx.coroutines.Dispatchers.Main) {
            suspendCancellableCoroutine<ImageProxy> { cont ->
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
        }

        val timestamp = System.currentTimeMillis()

        // ✅ Safe close with .use() (ImageProxy implements AutoCloseable)
        val bitmap = imageProxy.use { proxy ->
            proxy.toBitmap()
        }

        // Apply CPU post-processing off the main thread
        val postProcessor = CapturePostProcessor()
        val processed = withContext(kotlinx.coroutines.Dispatchers.Default) {
            postProcessor.apply(bitmap, profile, timestamp)
        }

        // Save to gallery on IO dispatcher
        val gallerySaver = GallerySaver(context)
        val uri = try {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                gallerySaver.save(processed, profile, timestamp)
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to save to gallery", e)
            Uri.EMPTY
        }

        return com.vintagecam.camera.CaptureResult(processed, uri, timestamp)
    }
    override fun switchCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val lifecycleOwner = boundLifecycleOwner ?: return
        val surfaceProvider = boundSurfaceProvider ?: return
        val provider = cameraProvider ?: return

        ContextCompat.getMainExecutor(context).execute {
            try {
                provider.unbindAll()
                bindInternal(lifecycleOwner, surfaceProvider)
            } catch (e: Exception) {
                android.util.Log.e("CameraXEngine", "Switch failed", e)
            }
        }
    }

    override fun setZoom(scale: Float) {}

    private fun bindInternal(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        val provider = cameraProvider ?: throw IllegalStateException("Camera provider unavailable")

        preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        provider.bindToLifecycle(
            lifecycleOwner,
            currentCameraSelector,
            preview,
            imageCapture,
        )
    }

    // Await extension for ListenableFuture tied to this instance's context
    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        try {
            addListener({
                try {
                    cont.resume(get()) {}
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }

    /**
     * Convert this ImageProxy to a Bitmap.
     *
     * Handles both [ImageFormat.JPEG] and [ImageFormat.YUV_420_888].
     * CameraX [ImageCapture.OnImageCapturedCallback] commonly returns JPEG —
     * the old code only handled YUV_420_888 and crashed on JPEG at planes[1].
     */
    private fun ImageProxy.toBitmap(): Bitmap {
        val format = this.format

        // --- JPEG: single plane with compressed bytes ---
        if (format == ImageFormat.JPEG) {
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        // --- YUV_420_888 → NV21 → JPEG ---
        if (format != ImageFormat.YUV_420_888) {
            android.util.Log.w(
                "CameraXEngine",
                "Unexpected ImageProxy format: $format — trying YUV fallback",
            )
        }

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uvSize = uBuffer.remaining()
        require(vBuffer.remaining() == uvSize) {
            "U/V plane sizes differ: uSize=$uvSize vSize=${vBuffer.remaining()}"
        }

        // NV21 layout: YYYY... VUVUVU...
        // The chroma region (size = 2 * uvSize) has V and U interleaved.
        val nv21 = ByteArray(ySize + 2 * uvSize)
        yBuffer.get(nv21, 0, ySize)

        // Interleave V and U for NV21 format — V at even positions, U at odd.
        // Bulk-copying all V then all U (the old approach) produces wrong colors.
        val vArr = ByteArray(uvSize)
        val uArr = ByteArray(uvSize)
        vBuffer.get(vArr)
        uBuffer.get(uArr)
        var chromaOffset = ySize
        for (i in 0 until uvSize) {
            nv21[chromaOffset++] = vArr[i]
            nv21[chromaOffset++] = uArr[i]
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
