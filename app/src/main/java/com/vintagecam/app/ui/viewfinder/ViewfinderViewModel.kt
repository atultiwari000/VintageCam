package com.vintagecam.app.ui.viewfinder

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vintagecam.app.audio.CameraSoundEngine
import com.vintagecam.camera.CameraEngine
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.captureTierInfo
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
    val developingCount: Int = 0,
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
                    current.copy(
                        capturedPhotos = photos,
                        developingCount = photos.count { it.isProcessing },
                    )
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
            val timestamp = System.currentTimeMillis()
            val pendingPhoto = SavedPhoto(
                id = "${profile.id}_$timestamp",
                filePath = "",
                profileId = profile.id,
                profileName = profile.displayName,
                timestampMillis = timestamp,
                isProcessing = true,
            )

            try {
                val totalStartMs = SystemClock.elapsedRealtime()
                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: start profile=${profile.id} tier=${profile.captureTierInfo().tier} frames=${profile.captureTierInfo().frameCount}",
                )
                sessionManager.addCapturedPhoto(pendingPhoto)

                _uiState.update { it.copy(cameraState = CameraState.Capturing) }
                cameraSoundEngine.playShutter(profile)
                delay(profile.captureLatencyMs)

                val rawStartMs = SystemClock.elapsedRealtime()
                val raw = cameraEngine.captureRawPhoto(profile, timestamp)
                val rawMs = SystemClock.elapsedRealtime() - rawStartMs
                _uiState.update { it.copy(cameraState = CameraState.Previewing) }

                android.util.Log.d("ViewfinderViewModel", "onCapture: raw frame captured id=${pendingPhoto.id} rawMs=$rawMs")

                val processStartMs = SystemClock.elapsedRealtime()
                val processed = cameraEngine.processPhoto(raw.bitmap, profile, raw.capturedAtMillis)
                val processMs = SystemClock.elapsedRealtime() - processStartMs

                val saveStartMs = SystemClock.elapsedRealtime()
                val savedPhoto = withContext(Dispatchers.IO) {
                    photoStore.save(
                        bitmap = processed,
                        profileId = profile.id,
                        profileName = profile.displayName,
                        timestamp = raw.capturedAtMillis,
                    )
                }
                val saveMs = SystemClock.elapsedRealtime() - saveStartMs

                android.util.Log.d("ViewfinderViewModel", "onCapture: saved id=${savedPhoto.id}")

                sessionManager.updateCapturedPhoto(savedPhoto)

                val totalMs = SystemClock.elapsedRealtime() - totalStartMs
                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: complete profile=${profile.id} rawMs=$rawMs processMs=$processMs saveMs=$saveMs totalMs=$totalMs",
                )
            } catch (e: Throwable) {
                android.util.Log.e("ViewfinderViewModel", "Capture failed", e)
                sessionManager.updateCapturedPhoto(
                    pendingPhoto.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Capture failed",
                    ),
                )
                _uiState.update { it.copy(cameraState = CameraState.Previewing) }
            }
        }
    }

    override fun onCleared() {
        cameraEngine.stopPreview()
        super.onCleared()
    }
}
