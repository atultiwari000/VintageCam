package com.vintagecam.profiles.data

import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.registry.FilterPreset
import com.vintagecam.profiles.registry.FilterRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val filterRegistry: FilterRegistry,
) {
    private val sortedProfiles: List<CameraProfile>
        get() = filterRegistry.getCameraProfiles().sortedBy { profile ->
            heroOrder.indexOf(profile.id).takeIf { it >= 0 } ?: (heroOrder.size + 1)
        }

    fun getProfiles(): List<CameraProfile> = sortedProfiles
    fun getProfileById(id: String): CameraProfile? = sortedProfiles.find { it.id == id }
    fun getDefaultProfile(): CameraProfile = sortedProfiles.first()
    fun getPresets(): List<FilterPreset> = filterRegistry.getPresets()
    fun getPresetById(id: String): FilterPreset? = filterRegistry.getPresetById(id)

    private companion object {
        val heroOrder = listOf(
            "disposable_1998",
            "vhs_1985",
            "digicam_2003",
            "polaroid_1990",
            "expired_instant",
            "super8_2020",
            "cinestill_800t",
        )
    }
}
