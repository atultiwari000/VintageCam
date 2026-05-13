package com.vintagecam.app.ui.gallery

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintagecam.profiles.data.PhotoStore
import com.vintagecam.profiles.data.SavedPhoto
import com.vintagecam.profiles.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryUiState(
    val photos: List<SavedPhoto> = emptyList(),
    val currentIndex: Int = 0,
    val fullScreenPhoto: SavedPhoto? = null,
    val fullScreenBitmap: Bitmap? = null,
    val isFullScreen: Boolean = false,
    val deleting: Boolean = false,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val photoStore: PhotoStore,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GalleryUiState(photos = sessionManager.getCurrentRollPhotos())
    )
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.capturedPhotos.collect { photos ->
                _uiState.update { it.copy(photos = photos) }
            }
        }
    }

    fun openFullScreen(index: Int) {
        val photo = _uiState.value.photos.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                currentIndex = index,
                fullScreenPhoto = photo,
                fullScreenBitmap = null,
                isFullScreen = true,
            )
        }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                photoStore.loadFull(photo.id)
            }
            _uiState.update {
                if (it.fullScreenPhoto?.id == photo.id) {
                    it.copy(fullScreenBitmap = bitmap)
                } else it
            }
        }
    }

    fun closeFullScreen() {
        _uiState.update {
            it.copy(fullScreenPhoto = null, fullScreenBitmap = null, isFullScreen = false)
        }
    }

    fun navigateNext() {
        val photos = _uiState.value.photos
        if (photos.isEmpty()) return
        val nextIndex = (_uiState.value.currentIndex + 1) % photos.size
        openFullScreen(nextIndex)
    }

    fun navigatePrevious() {
        val photos = _uiState.value.photos
        if (photos.isEmpty()) return
        val prevIndex = if (_uiState.value.currentIndex == 0) photos.size - 1 else _uiState.value.currentIndex - 1
        openFullScreen(prevIndex)
    }

    fun deleteCurrentPhoto() {
        val photo = _uiState.value.fullScreenPhoto ?: return
        val photos = _uiState.value.photos
        val deletedIndex = photos.indexOfFirst { it.id == photo.id }
        if (deletedIndex < 0) return

        _uiState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                sessionManager.deletePhoto(photo.id)
            }
            if (deleted) {
                val remaining = _uiState.value.photos
                if (remaining.isEmpty()) {
                    closeFullScreen()
                } else {
                    val newIndex = if (deletedIndex > 0) deletedIndex - 1 else 0
                    openFullScreen(newIndex.coerceIn(0, remaining.size - 1))
                }
            }
            _uiState.update { it.copy(deleting = false) }
        }
    }
}
