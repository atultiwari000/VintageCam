package com.vintagecam.app.audio

import android.content.Context
import com.vintagecam.profiles.CameraProfile

interface CameraSoundEngine {
    fun preload(context: Context, profiles: List<CameraProfile>)
    fun playShutter(profile: CameraProfile)
    fun release()
}
