package com.vintagecam.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.vintagecam.app.R
import com.vintagecam.profiles.CameraProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPoolEngine @Inject constructor() : CameraSoundEngine {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loadedSounds = mutableMapOf<String, Int>()

    override fun preload(context: Context, profiles: List<CameraProfile>) {
        if (loadedSounds.isNotEmpty()) return
        profiles.forEach { profile ->
            loadedSounds[profile.id] = soundPool.load(context, soundResFor(profile.id), 1)
        }
    }

    override fun playShutter(profile: CameraProfile) {
        val soundId = loadedSounds[profile.id]
        if (soundId != null && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
        } else {
            android.util.Log.w("SoundPoolEngine", "Sound not loaded for ${profile.id}")
        }
    }

    override fun release() {
        loadedSounds.clear()
        soundPool.release()
    }

    @RawRes
    private fun soundResFor(profileId: String): Int {
        return when (profileId) {
            "vhs_1985" -> R.raw.shutter_vhs
            "disposable_1998" -> R.raw.shutter_disposable
            "digicam_2003" -> R.raw.shutter_digicam
            else -> R.raw.shutter_default
        }
    }
}
