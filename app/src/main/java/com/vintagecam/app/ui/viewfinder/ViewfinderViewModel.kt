package com.vintagecam.app.ui.viewfinder

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        // Preload shutter sounds once at startup
        viewModelScope.launch {
            cameraSoundEngine.preload(appContext, profiles)
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
        // Guard: only allow capture when previewing
        if (_uiState.value.cameraState != CameraState.Previewing) return

        viewModelScope.launch {
            val profile = profiles.getOrNull(_uiState.value.currentProfileIndex) ?: return@launch

            try {
                // --- Capture sequence ---
                _uiState.update { it.copy(cameraState = CameraState.Capturing) }

                // Haptic handled by UI; play shutter
                cameraSoundEngine.playShutter(profile)

                // Simulate vintage shutter delay
                delay(profile.captureLatencyMs)

                _uiState.update { it.copy(cameraState = CameraState.Processing) }

                // Suspend until the camera pipeline produces a result
                val result: CaptureResult = cameraEngine.capturePhoto(profile)

                val capturedPhoto = CapturedPhoto(
                    bitmap = result.bitmap,
                    profile = profile,
                    timestampMillis = result.capturedAtMillis,
                    uri = result.uri,
                )

                // Persist to SessionManager and update UI state atomically
                sessionManager.addCapturedPhoto(capturedPhoto)

                _uiState.update { state ->
                    state.copy(
                        cameraState = CameraState.Previewing,
                        capturedPhotos = state.capturedPhotos + capturedPhoto,
                    )
                }

                android.util.Log.d(
                    "ViewfinderViewModel",
                    "Capture complete: profile=${profile.id} uri=${result.uri}",
                )
            } catch (e: Throwable) {
                // Catch Throwable (not just Exception) so that OOM, linkage errors,
                // or any other JVM Error still restore Previewing state instead of
                // leaving the UI stuck in Capturing/Processing.
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
