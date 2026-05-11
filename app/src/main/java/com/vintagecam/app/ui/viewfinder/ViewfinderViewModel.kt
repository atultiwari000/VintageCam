package com.vintagecam.app.ui.viewfinder

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintagecam.app.audio.CameraSoundEngine
import com.vintagecam.camera.CameraEngine
import com.vintagecam.camera.capture.CaptureEvent
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.data.CapturedPhoto
import com.vintagecam.profiles.data.ProfileRepository
import com.vintagecam.profiles.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
        profiles.firstOrNull()?.let(cameraEngine::applyProfile)
    }

    fun onStartPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        cameraEngine.startPreview(lifecycleOwner, surfaceProvider)
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
        viewModelScope.launch {
            val profile = profiles.getOrNull(_uiState.value.currentProfileIndex) ?: return@launch

            try {
                _uiState.update { it.copy(cameraState = CameraState.Capturing) }
                cameraEngine.capturePhoto(profile).collect { event ->
                    when (event) {
                        CaptureEvent.Processing -> {
                            cameraSoundEngine.playShutter(profile)
                            _uiState.update { it.copy(cameraState = CameraState.Processing) }
                        }
                        is CaptureEvent.Success -> {
                            val capturedPhoto = CapturedPhoto(
                                bitmap = event.bitmap,
                                profile = profile,
                                timestampMillis = event.capturedAtMillis,
                                uri = event.uri,
                            )
                            sessionManager.addCapturedPhoto(capturedPhoto)
                            _uiState.update { state ->
                                state.copy(
                                    cameraState = CameraState.Previewing,
                                    capturedPhotos = state.capturedPhotos + capturedPhoto,
                                )
                            }
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(cameraState = CameraState.Previewing) }
            }
        }
    }

    override fun onCleared() {
        cameraEngine.stopPreview()
        super.onCleared()
    }
}
