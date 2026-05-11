package com.vintagecam.profiles.data

import com.vintagecam.profiles.AspectRatio
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.DateStampStyle
import com.vintagecam.profiles.Era
import com.vintagecam.profiles.FlashBehavior
import com.vintagecam.profiles.ShaderType
import com.vintagecam.profiles.ViewfinderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor() {

    private val profiles: List<CameraProfile> = listOf(
        CameraProfile(
            id = "vhs_1985",
            displayName = "VHS 1985",
            era = Era.EIGHTIES,
            colorMatrix = floatArrayOf(
                1.02f, 0.01f, -0.03f,
                -0.05f, 1.08f, 0.02f,
                0.04f, -0.06f, 1.04f,
            ),
            vignetteStrength = 0.25f,
            grainIntensity = 0.12f,
            aspectRatio = AspectRatio.RATIO_4_3,
            viewfinderType = ViewfinderType.CRT,
            captureLatencyMs = 180L,
            shaderPipeline = listOf(
                ShaderType.COLOR_MATRIX,
                ShaderType.SCANLINES,
                ShaderType.CHROMATIC_ABERRATION,
                ShaderType.VIGNETTE,
                ShaderType.GRAIN,
            ),
            chromaticAberration = 0.015f,
            flashBehavior = FlashBehavior.OFF,
            dateStampStyle = DateStampStyle.NONE,
            interlacedPreview = true,
        ),
        CameraProfile(
            id = "disposable_1998",
            displayName = "FunSaver '98",
            era = Era.NINETIES,
            colorMatrix = floatArrayOf(
                1.08f, -0.06f, -0.05f,
                -0.06f, 1.16f, -0.04f,
                -0.05f, 0.02f, 0.95f,
            ),
            vignetteStrength = 0.45f,
            grainIntensity = 0.25f,
            aspectRatio = AspectRatio.RATIO_3_2,
            viewfinderType = ViewfinderType.OPTICAL,
            captureLatencyMs = 280L,
            shaderPipeline = listOf(
                ShaderType.COLOR_MATRIX,
                ShaderType.CRUSH_BLACKS,
                ShaderType.VIGNETTE,
                ShaderType.GRAIN,
                ShaderType.DATE_STAMP,
            ),
            chromaticAberration = 0.005f,
            flashBehavior = FlashBehavior.AUTO,
            dateStampStyle = DateStampStyle.YELLOW_CLASSIC,
            crushedBlacks = 0.35f,
        ),
        CameraProfile(
            id = "digicam_2003",
            displayName = "CyberShot 2003",
            era = Era.TWO_THOUSANDS,
            colorMatrix = floatArrayOf(
                1.12f, -0.05f, -0.03f,
                -0.10f, 1.18f, -0.06f,
                -0.04f, -0.08f, 1.16f,
            ),
            vignetteStrength = 0.18f,
            grainIntensity = 0.16f,
            aspectRatio = AspectRatio.RATIO_4_3,
            viewfinderType = ViewfinderType.LCD,
            captureLatencyMs = 400L,
            shaderPipeline = listOf(
                ShaderType.COLOR_MATRIX,
                ShaderType.SHARPEN,
                ShaderType.SHADOW_NOISE,
                ShaderType.VIGNETTE,
                ShaderType.GRAIN,
            ),
            chromaticAberration = 0.004f,
            flashBehavior = FlashBehavior.RED_EYE,
            dateStampStyle = DateStampStyle.WHITE_LCD,
            shadowNoiseIntensity = 0.30f,
        ),
    )

    fun getProfiles(): List<CameraProfile> = profiles

    fun getProfileById(id: String): CameraProfile? = profiles.firstOrNull { it.id == id }

    fun getDefaultProfile(): CameraProfile = profiles.first()
}
