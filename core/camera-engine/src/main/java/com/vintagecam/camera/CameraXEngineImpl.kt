package com.vintagecam.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.vintagecam.camera.capture.ComputationalBurstProcessor
import com.vintagecam.camera.capture.GallerySaver
import com.vintagecam.camera.capture.NativeFilterProcessor
import com.vintagecam.camera.capture.toBitmap
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.ComputationalMode
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
import java.util.concurrent.TimeUnit

@Singleton
class CameraXEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureProcessor: NativeFilterProcessor,
) : CameraEngine {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var currentProfile: CameraProfile? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundSurfaceProvider: Preview.SurfaceProvider? = null
    private var flashEnabled: Boolean = false

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
            camera = null
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to stop preview", e)
        }
    }

    override suspend fun captureRawPhoto(
        profile: CameraProfile,
        capturedAtMillis: Long,
    ): RawCaptureResult {
        val burstCount = profile.effectiveBurstFrameCount()
        if (burstCount > 1) {
            return captureRawBurst(profile, capturedAtMillis, burstCount)
        }

        val bitmap = captureBitmapFrame(profile)
        return RawCaptureResult(bitmap, capturedAtMillis)
    }

    private suspend fun captureRawBurst(
        profile: CameraProfile,
        capturedAtMillis: Long,
        burstCount: Int,
    ): RawCaptureResult {
        android.util.Log.d("CameraXEngine", "captureRawBurst: begin profile=${profile.id} frames=$burstCount")
        val firstFrame = captureBitmapFrame(profile)
        val mergeFrames = ArrayList<android.graphics.Bitmap>(burstCount - 1)

        repeat(burstCount - 1) { index ->
            val frame = captureBitmapFrame(profile)
            mergeFrames += frame
            if (index < burstCount - 2) delay(18)
        }

        android.util.Log.d("CameraXEngine", "captureRawBurst: collected profile=${profile.id} frames=$burstCount")
        return RawCaptureResult(firstFrame, capturedAtMillis, mergeFrames)
    }

    private suspend fun captureBitmapFrame(profile: CameraProfile): android.graphics.Bitmap {
        val capture = imageCapture ?: throw IllegalStateException("Camera not started")

        android.util.Log.d("CameraXEngine", "captureRawPhoto: begin profile=${profile.id}")

        val imageProxy = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<ImageProxy> { cont ->
                try {
                    android.util.Log.d("CameraXEngine", "captureRawPhoto: takePicture requested profile=${profile.id}")
                    capture.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                android.util.Log.d("CameraXEngine", "captureRawPhoto: onCaptureSuccess profile=${profile.id}")
                                cont.resume(image)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                android.util.Log.e("CameraXEngine", "captureRawPhoto: onError profile=${profile.id}", exception)
                                cont.resumeWithException(exception)
                            }
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CameraXEngine", "captureRawPhoto: takePicture threw profile=${profile.id}", e)
                    cont.resumeWithException(e)
                }
            }
        }

        android.util.Log.d("CameraXEngine", "captureRawPhoto: got imageProxy profile=${profile.id}")

        val bitmap = withContext(Dispatchers.Default) {
            imageProxy.use { proxy ->
                proxy.toBitmap()
            }
        }

        return bitmap
    }

    override suspend fun processPhoto(
        bitmap: android.graphics.Bitmap,
        profile: CameraProfile,
        capturedAtMillis: Long,
    ): android.graphics.Bitmap = withContext(Dispatchers.Default) {
        captureProcessor.processBitmap(bitmap, profile, capturedAtMillis)
    }

    override suspend fun processPhoto(
        raw: RawCaptureResult,
        profile: CameraProfile,
    ): android.graphics.Bitmap {
        val source = if (raw.mergeFrames.isEmpty()) {
            raw.bitmap
        } else {
            withContext(Dispatchers.Default) {
                val accumulator = ComputationalBurstProcessor.begin(
                    reference = raw.bitmap,
                    profile = profile,
                    totalFrames = raw.mergeFrames.size + 1,
                )
                raw.mergeFrames.forEach { frame ->
                    accumulator.addFrame(frame)
                    if (!frame.isRecycled) frame.recycle()
                }
                accumulator.finish()
            }.also {
                if (!raw.bitmap.isRecycled) raw.bitmap.recycle()
            }
        }

        var processed: android.graphics.Bitmap? = null
        return try {
            processPhoto(source, profile, raw.capturedAtMillis).also {
                processed = it
            }
        } finally {
            if (processed !== source && !source.isRecycled) source.recycle()
        }
    }

    override suspend fun capturePhoto(profile: CameraProfile): CaptureResult {
        val raw = captureRawPhoto(profile, System.currentTimeMillis())
        val processed = processPhoto(raw, profile)

        // Save to gallery on IO dispatcher
        val gallerySaver = GallerySaver(context)
        val uri = try {
            withContext(Dispatchers.IO) {
                android.util.Log.d("CameraXEngine", "capturePhoto: saving to gallery profile=${profile.id}")
                gallerySaver.save(processed, profile, raw.capturedAtMillis)
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraXEngine", "Failed to save to gallery", e)
            Uri.EMPTY
        }

        android.util.Log.d("CameraXEngine", "capturePhoto: complete profile=${profile.id} uri=$uri")

        return CaptureResult(processed, uri, raw.capturedAtMillis)
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
                setFlashEnabled(flashEnabled)
            } catch (e: Exception) {
                android.util.Log.e("CameraXEngine", "Switch failed", e)
            }
        }
    }

    override fun setFlashEnabled(enabled: Boolean) {
        flashEnabled = enabled
        imageCapture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

        val boundCamera = camera ?: return
        if (!boundCamera.cameraInfo.hasFlashUnit()) {
            android.util.Log.w("CameraXEngine", "Flash requested but this camera has no flash unit")
            return
        }

        boundCamera.cameraControl.enableTorch(enabled)
    }

    override fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val boundCamera = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        boundCamera.cameraControl.startFocusAndMetering(action)
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
            .also {
                it.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            }

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            currentCameraSelector,
            preview,
            imageCapture,
        )
        setFlashEnabled(flashEnabled)
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

    private fun CameraProfile.effectiveBurstFrameCount(): Int {
        if (computationalMode == ComputationalMode.SINGLE) return 1
        if (noiseReductionStrength <= 0f && toneRecoveryStrength <= 0f && portraitEnhancementStrength <= 0f) return 1
        return burstFrameCount.coerceIn(1, 8)
    }
}
