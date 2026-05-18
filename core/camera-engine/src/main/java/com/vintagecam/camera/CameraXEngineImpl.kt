package com.vintagecam.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.vintagecam.camera.capture.GallerySaver
import com.vintagecam.camera.capture.NativeFilterProcessor
import com.vintagecam.profiles.CameraProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraXEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureProcessor: NativeFilterProcessor,
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

            var retries = 3
            while (retries > 0) {
                try {
                    lifecycleOwner.lifecycle.awaitStarted()
                    bindInternal(lifecycleOwner, surfaceProvider)
                    return@withContext
                } catch (e: Exception) {
                    retries--
                    if (retries == 0) throw e
                    android.util.Log.w("CameraXEngine", "startPreview: bind failed, ${retries} retries left", e)
                    delay(300)
                }
            }
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

        // Capture ImageProxy on main thread
        val imageProxy = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<ImageProxy> { cont ->
                try {
                    android.util.Log.d("CameraXEngine", "capturePhoto: takePicture requested profile=${profile.id}")
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                android.util.Log.d("CameraXEngine", "capturePhoto: onCaptureSuccess profile=${profile.id}")
                                cont.resume(image)
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

        // Process off the main thread and always close ImageProxy afterward.
        val processed = withContext(Dispatchers.Default) {
            imageProxy.use { proxy ->
                captureProcessor.process(proxy, profile, timestamp)
            }
        }

        // Save to gallery on IO dispatcher
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

        // Guard: lifecycle must be at least STARTED before binding camera use cases
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            throw IllegalStateException(
                "Cannot bind camera: lifecycle is ${lifecycleOwner.lifecycle.currentState}, need STARTED"
            )
        }

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
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }

    private suspend fun Lifecycle.awaitStarted() {
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (currentState == Lifecycle.State.DESTROYED) {
            throw IllegalStateException("Cannot start camera: lifecycle is destroyed")
        }

        val lifecycle = this
        suspendCancellableCoroutine<Unit> { cont ->
            lateinit var observer: LifecycleEventObserver
            observer = LifecycleEventObserver { _, event ->
                when {
                    lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> {
                        lifecycle.removeObserver(observer)
                        cont.resume(Unit)
                    }
                    event == Lifecycle.Event.ON_DESTROY -> {
                        lifecycle.removeObserver(observer)
                        cont.resumeWithException(
                            IllegalStateException("Cannot start camera: lifecycle was destroyed"),
                        )
                    }
                }
            }

            lifecycle.addObserver(observer)
            cont.invokeOnCancellation {
                lifecycle.removeObserver(observer)
            }
        }
    }
}
