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

    private val profiles = listOf(
        CameraProfile(
            id = "vhs_1985",
            displayName = "VHS-C 1985",
            era = Era.EIGHTIES,
            colorMatrix = floatArrayOf(
                1.05f, -0.02f, -0.08f,
                -0.05f, 1.10f, -0.05f,
                0.02f, -0.10f, 1.08f
            ),
            vignetteStrength = 0.25f,
            grainIntensity = 0.12f,
            aspectRatio = AspectRatio.RATIO_4_3,
            viewfinderType = ViewfinderType.CRT,
            captureLatencyMs = 150L,
            shaderPipeline = listOf(ShaderType.COLOR_MATRIX, ShaderType.VIGNETTE, ShaderType.GRAIN),
            chromaticAberration = 0.015f,
            interlacedPreview = true
        ),
        CameraProfile(
            id = "disposable_1998",
            displayName = "FunSaver '98",
            era = Era.NINETIES,
            colorMatrix = floatArrayOf(
                1.10f, -0.05f, -0.10f,
                -0.10f, 1.20f, -0.10f,
                -0.05f, -0.15f, 1.20f
            ),
            vignetteStrength = 0.45f,
            grainIntensity = 0.25f,
            aspectRatio = AspectRatio.RATIO_3_2,
            viewfinderType = ViewfinderType.OPTICAL,
            captureLatencyMs = 80L,
            shaderPipeline = listOf(ShaderType.COLOR_MATRIX, ShaderType.VIGNETTE, ShaderType.GRAIN),
            dateStampStyle = DateStampStyle.YELLOW_CLASSIC,
            crushedBlacks = 0.05f
        ),
        CameraProfile(
            id = "digicam_2003",
            displayName = "CyberShot 2003",
            era = Era.TWO_THOUSANDS,
            colorMatrix = floatArrayOf(
                1.20f, -0.10f, -0.10f,
                -0.15f, 1.30f, -0.15f,
                -0.05f, -0.10f, 1.15f
            ),
            vignetteStrength = 0.10f,
            grainIntensity = 0.08f,
            aspectRatio = AspectRatio.RATIO_4_3,
            viewfinderType = ViewfinderType.LCD,
            captureLatencyMs = 400L,
            shaderPipeline = listOf(ShaderType.COLOR_MATRIX, ShaderType.SHARPEN, ShaderType.GRAIN),
            dateStampStyle = DateStampStyle.WHITE_LCD,
            flashBehavior = FlashBehavior.RED_EYE
        )
    )
    
    fun getProfiles(): List<CameraProfile> = profiles
    fun getProfileById(id: String): CameraProfile? = profiles.find { it.id == id }
    fun getDefaultProfile(): CameraProfile = profiles.first()
}
