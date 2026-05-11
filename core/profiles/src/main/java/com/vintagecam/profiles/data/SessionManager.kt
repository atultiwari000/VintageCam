package com.vintagecam.profiles.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {
    private val currentRollPhotos = mutableListOf<CapturedPhoto>()

    @Synchronized
    fun addCapturedPhoto(photo: CapturedPhoto) {
        currentRollPhotos += photo
    }

    @Synchronized
    fun getCurrentRollPhotos(): List<CapturedPhoto> = currentRollPhotos.toList()

    @Synchronized
    fun clearCurrentRoll() {
        currentRollPhotos.clear()
    }
}
