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
import com.vintagecam.camera.capture.GallerySaver
import com.vintagecam.camera.filter.FilterFactory
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
    @ApplicationContext private val context: Context,
    private val filterFactory: FilterFactory,
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

        withContext(Dispatchers.Main) {
            val provider = ProcessCameraProvider.getInstance(context).await()
            cameraProvider = provider
            bindInternal(lifecycleOwner, surfaceProvider)
        }
    }

    override fun stopPreview() {
        try {
            cameraProvider?.unbindAll()
            preview = null
            imageCapture = null
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to stop preview", e)
        }
    }

    override suspend fun capturePhoto(profile: CameraProfile): CaptureResult {
        val capture = imageCapture ?: throw IllegalStateException("Camera not started")

        android.util.Log.d("CameraXEngine", "capturePhoto: begin profile=${profile.id}")

        val imageProxy = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<ImageProxy> { cont ->
                try {
                    android.util.Log.d("CameraXEngine", "capturePhoto: takePicture requested profile=${profile.id}")
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                android.util.Log.d("CameraXEngine", "capturePhoto: onCaptureSuccess profile=${profile.id}")
                                cont.resume(image) {}
                            }
                            override fun onError(exception: ImageCaptureException) {
                                android.util.Log.e("CameraXEngine", "capturePhoto: onError profile=${profile.id}", exception)
                                cont.resumeWithException(exception)
                            }
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CameraXEngine", "capturePhoto: takePicture threw profile=${profile.id}", e)
                    cont.resumeWithException(e)
                }
            }
        }

        val timestamp = System.currentTimeMillis()
        android.util.Log.d("CameraXEngine", "capturePhoto: got imageProxy profile=${profile.id}")
        val bitmap = imageProxy.use { proxy ->
            proxy.toBitmap()
        }

        android.util.Log.d("CameraXEngine", "capturePhoto: converted bitmap profile=${profile.id} size=${bitmap.width}x${bitmap.height}")

        val processed = withContext(Dispatchers.Default) {
            android.util.Log.d("CameraXEngine", "capturePhoto: post-processing begin profile=${profile.id}")
            filterFactory.getFilter(profile.id).apply(bitmap, profile, timestamp)
        }

        android.util.Log.d("CameraXEngine", "capturePhoto: post-processing complete profile=${profile.id} size=${processed.width}x${processed.height}")

        val gallerySaver = GallerySaver(context)
        val uri = try {
            withContext(Dispatchers.IO) {
                android.util.Log.d("CameraXEngine", "capturePhoto: saving to gallery profile=${profile.id}")
                gallerySaver.save(processed, profile, timestamp)
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to save to gallery", e)
            Uri.EMPTY
        }

        android.util.Log.d("CameraXEngine", "capturePhoto: complete profile=${profile.id} uri=$uri")

        return CaptureResult(processed, uri, timestamp)
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

        // CRITICAL FIX: Unbind old use cases before creating new ones
        provider.unbindAll()

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

    private fun ImageProxy.toBitmap(): Bitmap {
        val format = this.format

        if (format == ImageFormat.JPEG) {
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Failed to decode JPEG bitmap")
        }

        if (format != ImageFormat.YUV_420_888) {
            android.util.Log.w("CameraXEngine", "Unexpected format: $format — trying YUV fallback")
        }

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uvSize = uBuffer.remaining()

        val nv21 = ByteArray(ySize + 2 * uvSize)
        yBuffer.get(nv21, 0, ySize)

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
            ?: throw IllegalStateException("Failed to decode YUV bitmap")
    }
}