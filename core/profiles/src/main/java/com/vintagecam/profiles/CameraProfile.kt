package com.vintagecam.profiles

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraProfile(
	val id: String,
	val displayName: String,
	val era: Era,
	val colorMatrix: FloatArray,
	val vignetteStrength: Float,
	val grainIntensity: Float,
	val aspectRatio: AspectRatio,
	val viewfinderType: ViewfinderType,
	val captureLatencyMs: Long,
	val shaderPipeline: List<ShaderType>,
	val chromaticAberration: Float = 0f,
	val flashBehavior: FlashBehavior = FlashBehavior.OFF,
	val dateStampStyle: DateStampStyle = DateStampStyle.NONE,
	val crushedBlacks: Float = 0f,
	val shadowNoiseIntensity: Float = 0f,
	val interlacedPreview: Boolean = false,
	val previewVignetteAlpha: Int = 100,
	val previewGrainIntensity: Float = 0.15f,
	val deviceLabel: String = displayName,
	val categoryLabel: String = era.name,
	val tierLabel: String = "FREE",
	val assetStatusLabel: String = "AVAILABLE",
	val effects: List<String> = emptyList(),
	val lutAssetPath: String? = null,
) : Parcelable

enum class Era {
	EIGHTIES,
	NINETIES,
	TWO_THOUSANDS,
}

enum class AspectRatio(val width: Int, val height: Int) {
	RATIO_4_3(4, 3),
	RATIO_3_2(3, 2),
	RATIO_1_1(1, 1),
	RATIO_16_9(16, 9),
}

enum class ViewfinderType {
	CRT,
	OPTICAL,
	LCD,
}

enum class FlashBehavior {
	OFF,
	AUTO,
	ON,
	RED_EYE,
}

enum class DateStampStyle {
	NONE,
	YELLOW_CLASSIC,
	RED_LED,
	WHITE_LCD,
}

enum class ShaderType {
	COLOR_MATRIX,
	VIGNETTE,
	GRAIN,
	SCANLINES,
	CHROMATIC_ABERRATION,
	CRUSH_BLACKS,
	SHARPEN,
	SHADOW_NOISE,
	DATE_STAMP,
}
