package com.vintagecam.profiles.data

import android.util.Log
import com.vintagecam.profiles.data.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks captured photos for the current session.
 *
 * On init, loads ALL previously-saved photos from [PhotoStore] so the
 * film roll survives process death. New captures are persisted immediately.
 *
 * Exposes a [StateFlow] so UI layers can observe changes reactively.
 */
@Singleton
class SessionManager @Inject constructor(
    private val photoStore: PhotoStore,
) {
    private val _capturedPhotos = MutableStateFlow<List<SavedPhoto>>(emptyList())

    /** Observable stream of all captured photos. Survives process death. */
    val capturedPhotos: StateFlow<List<SavedPhoto>> = _capturedPhotos.asStateFlow()

    init {
        val existing = photoStore.loadAll()
        if (existing.isNotEmpty()) {
            _capturedPhotos.value = existing
            Log.d("SessionManager", "Restored ${existing.size} photos from disk")
        }
    }

    /** Add a photo to the session roll. Photo is already persisted by caller. */
    fun addCapturedPhoto(photo: SavedPhoto) {
        _capturedPhotos.update { current ->
            if (current.any { it.id == photo.id }) {
                current.map { if (it.id == photo.id) photo else it }
            } else {
                listOf(photo) + current
            }
        }
        Log.d("SessionManager", "addCapturedPhoto: ${photo.id} — roll size ${_capturedPhotos.value.size}")
    }

    /** Replace an existing roll item, usually when a pending frame finishes developing. */
    fun updateCapturedPhoto(photo: SavedPhoto) {
        _capturedPhotos.update { current ->
            current.map { if (it.id == photo.id) photo else it }
        }
        Log.d("SessionManager", "updateCapturedPhoto: ${photo.id} — processing=${photo.isProcessing}")
    }

    /** Delete a photo from the roll and from disk. */
    fun deletePhoto(id: String): Boolean {
        val deleted = photoStore.delete(id)
        if (deleted) {
            _capturedPhotos.update { current -> current.filter { it.id != id } }
            Log.d("SessionManager", "deletePhoto: $id — roll size ${_capturedPhotos.value.size}")
        }
        return deleted
    }

    /** Clear all photos from memory (does not delete files). */
    fun clearCurrentRoll() {
        _capturedPhotos.value = emptyList()
    }

    /** Convenience snapshot. Prefer collecting [capturedPhotos]. */
    fun getCurrentRollPhotos(): List<SavedPhoto> = _capturedPhotos.value

    /** Number of photos in the roll. */
    fun photoCount(): Int = _capturedPhotos.value.size
}
