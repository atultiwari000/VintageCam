package com.vintagecam.profiles.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks captured photos for the current camera session.
 *
 * Exposes a [StateFlow] so UI layers can observe changes reactively
 * instead of polling with snapshots. All mutations are thread-safe
 * via [MutableStateFlow.update] (atomic CAS).
 */
@Singleton
class SessionManager @Inject constructor() {

    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())

    /**
     * Observable stream of all photos captured in this session.
     * Collect from this in ViewModels or Composables.
     */
    val capturedPhotos: StateFlow<List<CapturedPhoto>> = _capturedPhotos.asStateFlow()

    /**
     * Add a photo to the current session roll.
     * Immediately visible to all collectors of [capturedPhotos].
     */
    fun addCapturedPhoto(photo: CapturedPhoto) {
        _capturedPhotos.update { listOf(photo) + it }
        android.util.Log.d(
            "SessionManager",
            "addCapturedPhoto: ${photo.profile.id} — roll size now ${_capturedPhotos.value.size}",
        )
    }

    /**
     * Clear all photos from the current session.
     */
    fun clearCurrentRoll() {
        _capturedPhotos.value = emptyList()
        android.util.Log.d("SessionManager", "clearCurrentRoll: roll cleared")
    }

    /**
     * Convenience accessor for call-sites that need a snapshot.
     *
     * Prefer collecting [capturedPhotos] instead.
     */
    fun getCurrentRollPhotos(): List<CapturedPhoto> = _capturedPhotos.value
}
