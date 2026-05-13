package com.vintagecam.camera.filter

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterFactory @Inject constructor() {
    private val filters = mapOf(
        "vhs_1985" to Vhs1985Filter(),
        "disposable_1998" to FunSaver1998Filter(),
        "digicam_2003" to CyberShot2003Filter(),
    )

    fun getFilter(profileId: String): CameraFilter {
        return filters[profileId] ?: filters["vhs_1985"]!!
    }
}
