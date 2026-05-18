package com.vintagecam.camera.filter

import com.vintagecam.imageprocessor.NativeImageProcessor
import com.vintagecam.camera.capture.CapturePostProcessor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterFactory @Inject constructor(
    private val nativeProcessor: NativeImageProcessor,
    private val postProcessor: CapturePostProcessor,
) {
    private val kotlinFilters = mapOf(
        "vhs_1985" to Vhs1985Filter(),
        "disposable_1998" to FunSaver1998Filter(),
        "digicam_2003" to CyberShot2003Filter(),
    )

    /**
     * Returns a [CameraFilter] for the given profile ID.
     *
     * When native processing is available, returns a [NativeCameraFilter]
     * that delegates to the C++ image processor. Otherwise falls back
     * to the existing Kotlin CPU filter implementations.
     */
    fun getFilter(profileId: String): CameraFilter {
        return if (nativeProcessor.isAvailable()) {
            NativeCameraFilter(profileId, nativeProcessor)
        } else {
            getKotlinFilter(profileId)
        }
    }

    /**
     * Explicitly request the Kotlin fallback filter regardless of
     * native availability (e.g. for testing or diagnostics).
     */
    fun getKotlinFilter(profileId: String): CameraFilter {
        return kotlinFilters[profileId] ?: ParameterCameraFilter(profileId, postProcessor)
    }
}
