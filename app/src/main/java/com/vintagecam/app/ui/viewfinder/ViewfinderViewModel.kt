package com.vintagecam.app.ui.viewfinder

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintagecam.camera.CameraEngine
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.data.ProfileRepository
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
    val selectedProfileIndex: Int = 0,
    val flashEnabled: Boolean = false,
)

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    private val cameraEngine: CameraEngine,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val profiles: List<CameraProfile> = profileRepository.getProfiles()

    private val _uiState = MutableStateFlow(
        ViewfinderUiState(
            profiles = profiles,
            selectedProfileIndex = 0,
        ),
    )
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    init {
        profiles.firstOrNull()?.let(cameraEngine::applyProfile)
    }

    fun onStartPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraEngine.startPreview(lifecycleOwner, previewView.surfaceProvider)
    }

    fun onProfileSelected(index: Int) {
        if (index !in profiles.indices) return
        cameraEngine.applyProfile(profiles[index])
        _uiState.update { current ->
            current.copy(selectedProfileIndex = index, cameraState = CameraState.Previewing)
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
            _uiState.update { it.copy(cameraState = CameraState.Capturing) }
            cameraEngine.capturePhoto().collect {
                _uiState.update { state -> state.copy(cameraState = CameraState.Processing) }
                _uiState.update { state -> state.copy(cameraState = CameraState.Previewing) }
            }
        }
    }

    override fun onCleared() {
        cameraEngine.stopPreview()
        super.onCleared()
    }
}
