package com.vintagecam.app.ui.viewfinder

import android.content.Context
import android.view.View
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vintagecam.app.audio.CameraSoundEngine
import com.vintagecam.camera.CameraEngine
import com.vintagecam.camera.CaptureResult
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.data.PhotoStore
import com.vintagecam.profiles.data.SavedPhoto
import com.vintagecam.profiles.data.ProfileRepository
import com.vintagecam.profiles.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

sealed interface CameraState {
    data object Previewing : CameraState
    data object Capturing : CameraState
    data object Processing : CameraState
}

data class ViewfinderUiState(
    val cameraState: CameraState = CameraState.Previewing,
    val profiles: List<CameraProfile> = emptyList(),
    val currentProfileIndex: Int = 0,
    val flashEnabled: Boolean = false,
    val capturedPhotos: List<SavedPhoto> = emptyList(),
)

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cameraEngine: CameraEngine,
    private val cameraSoundEngine: CameraSoundEngine,
    private val sessionManager: SessionManager,
    private val photoStore: PhotoStore,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private companion object {
        private const val MIN_DEVELOPMENT_WINDOW_MS = 1800L
    }

    private val profiles: List<CameraProfile> = profileRepository.getProfiles()

    private val _uiState = MutableStateFlow(
        ViewfinderUiState(
            profiles = profiles,
            currentProfileIndex = 0,
            capturedPhotos = sessionManager.getCurrentRollPhotos(),
        ),
    )
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cameraSoundEngine.preload(appContext, profiles)
        }

        viewModelScope.launch {
            sessionManager.capturedPhotos.collect { photos ->
                _uiState.update { current ->
                    current.copy(capturedPhotos = photos)
                }
            }
        }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        viewModelScope.launch {
            try {
                // ── Fix: wait for PreviewView to be attached before binding camera ──
                // Calling startPreview before the view is attached triggers
                // "Camera is closed" because the surface isn't ready yet.
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        if (previewView.isAttachedToWindow) {
                            cont.resume(Unit) {}
                        } else {
                            val listener = object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) {
                                    previewView.removeOnAttachStateChangeListener(this)
                                    cont.resume(Unit) {}
                                }
                                override fun onViewDetachedFromWindow(v: View) {}
                            }
                            previewView.addOnAttachStateChangeListener(listener)
                            cont.invokeOnCancellation {
                                previewView.removeOnAttachStateChangeListener(listener)
                            }
                        }
                    }
                }
                cameraEngine.startPreview(lifecycleOwner, previewView.surfaceProvider)
            } catch (e: Exception) {
                android.util.Log.e("ViewfinderViewModel", "Failed to bind camera", e)
            }
        }
    }

    fun onStopPreview() {
        android.util.Log.d("ViewfinderViewModel", "onStopPreview: stopping camera preview")
        cameraEngine.stopPreview()
    }

    fun onProfileSelected(index: Int) {
        if (index !in profiles.indices) return
        cameraEngine.applyProfile(profiles[index])
        _uiState.update { current ->
            current.copy(currentProfileIndex = index, cameraState = CameraState.Previewing)
        }
    }

    fun onToggleFlash() {
        _uiState.update { current ->
            current.copy(flashEnabled = !current.flashEnabled)
        }
    }

    fun onSwitchCamera() {
        cameraEngine.switchCamera()
    }

    fun onCapture() {
        if (_uiState.value.cameraState != CameraState.Previewing) return

        viewModelScope.launch {
            val profile = profiles.getOrNull(_uiState.value.currentProfileIndex) ?: return@launch

            try {
                android.util.Log.d("ViewfinderViewModel", "onCapture: start profile=${profile.id}")

                _uiState.update { it.copy(cameraState = CameraState.Capturing) }
                cameraSoundEngine.playShutter(profile)
                delay(profile.captureLatencyMs)
                _uiState.update { it.copy(cameraState = CameraState.Processing) }
                val processingStartedAt = SystemClock.elapsedRealtime()

                val result: CaptureResult = cameraEngine.capturePhoto(profile)

                android.util.Log.d("ViewfinderViewModel", "onCapture: capturePhoto returned profile=${profile.id}")

                val savedPhoto = withContext(Dispatchers.IO) {
                    photoStore.save(
                        bitmap = result.bitmap,
                        profileId = profile.id,
                        profileName = profile.displayName,
                        timestamp = result.capturedAtMillis,
                    )
                }

                android.util.Log.d("ViewfinderViewModel", "onCapture: saved id=${savedPhoto.id}")

                sessionManager.addCapturedPhoto(savedPhoto)

                val processingElapsedMs = SystemClock.elapsedRealtime() - processingStartedAt
                val remainingMs = MIN_DEVELOPMENT_WINDOW_MS - processingElapsedMs
                if (remainingMs > 0) {
                    delay(remainingMs)
                } else {
                    yield()
                }

                _uiState.update { it.copy(cameraState = CameraState.Previewing) }

                android.util.Log.d("ViewfinderViewModel", "onCapture: complete profile=${profile.id}")
            } catch (e: Throwable) {
                android.util.Log.e("ViewfinderViewModel", "Capture failed", e)
                _uiState.update { it.copy(cameraState = CameraState.Previewing) }
            }
        }
    }

    override fun onCleared() {
        cameraEngine.stopPreview()
        super.onCleared()
    }
}
