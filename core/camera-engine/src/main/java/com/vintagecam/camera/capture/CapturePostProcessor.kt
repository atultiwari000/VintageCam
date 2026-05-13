package com.vintagecam.camera.capture

import android.graphics.Bitmap
import com.vintagecam.camera.filter.FilterFactory
import com.vintagecam.profiles.CameraProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin delegating wrapper around [FilterFactory].
 *
 * Each [CameraProfile] now has its own dedicated filter class
 * (e.g. [com.vintagecam.camera.filter.Vhs1985Filter],
 *  [com.vintagecam.camera.filter.FunSaver1998Filter],
 *  [com.vintagecam.camera.filter.CyberShot2003Filter]).
 *
 * This class exists to keep the public API stable for existing callers.
 * New code should inject [FilterFactory] directly.
 */
@Singleton
class CapturePostProcessor @Inject constructor(
    private val filterFactory: FilterFactory,
) {
    fun apply(input: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap {
        val filter = filterFactory.getFilter(profile.id)
        return filter.apply(input, profile, capturedAtMillis)
    }
}
