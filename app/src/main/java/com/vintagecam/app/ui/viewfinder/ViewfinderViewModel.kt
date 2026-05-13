package com.vintagecam.app.ui.viewfinder

import android.content.Context
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
import com.vintagecam.profiles.data.CapturedPhoto
import com.vintagecam.profiles.data.ProfileRepository
import com.vintagecam.profiles.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val capturedPhotos: List<CapturedPhoto> = emptyList(),
)

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cameraEngine: CameraEngine,
    private val cameraSoundEngine: CameraSoundEngine,
    private val sessionManager: SessionManager,
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
                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: start profile=${profile.id} state=${_uiState.value.cameraState}",
                )
                _uiState.update { it.copy(cameraState = CameraState.Capturing) }
                cameraSoundEngine.playShutter(profile)
                delay(profile.captureLatencyMs)
                _uiState.update { it.copy(cameraState = CameraState.Processing) }
                val processingStartedAt = SystemClock.elapsedRealtime()

                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: processing started profile=${profile.id}",
                )

                val result: CaptureResult = cameraEngine.capturePhoto(profile)

                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: capturePhoto returned profile=${profile.id} uri=${result.uri}",
                )

                val capturedPhoto = CapturedPhoto(
                    bitmap = result.bitmap,
                    profile = profile,
                    timestampMillis = result.capturedAtMillis,
                    uri = result.uri,
                )

                sessionManager.addCapturedPhoto(capturedPhoto)

                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: added to session roll profile=${profile.id}",
                )

                val processingElapsedMs = SystemClock.elapsedRealtime() - processingStartedAt
                val remainingDevelopmentMs = MIN_DEVELOPMENT_WINDOW_MS - processingElapsedMs
                if (remainingDevelopmentMs > 0) {
                    android.util.Log.d(
                        "ViewfinderViewModel",
                        "onCapture: holding processing for ${remainingDevelopmentMs}ms profile=${profile.id}",
                    )
                    delay(remainingDevelopmentMs)
                } else {
                    android.util.Log.d(
                        "ViewfinderViewModel",
                        "onCapture: development window already satisfied profile=${profile.id}",
                    )
                    yield()
                }

                _uiState.update { state ->
                    state.copy(cameraState = CameraState.Previewing)
                }

                android.util.Log.d(
                    "ViewfinderViewModel",
                    "onCapture: returned to preview profile=${profile.id}",
                )

                android.util.Log.d(
                    "ViewfinderViewModel",
                    "Capture complete: profile=${profile.id} uri=${result.uri}",
                )
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