package com.vintagecam.profiles.registry

import com.vintagecam.profiles.AspectRatio
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.DateStampStyle
import com.vintagecam.profiles.Era
import com.vintagecam.profiles.FlashBehavior
import com.vintagecam.profiles.ShaderType
import com.vintagecam.profiles.ViewfinderType

data class FilterPreset(
    val id: String,
    val name: String,
    val category: FilterCategory,
    val tier: FilterTier,
    val parameters: FilterParameters,
    val assets: FilterAssets,
    val captureHints: CaptureHints,
    val effects: List<FilterEffect>,
    val unlockCondition: UnlockCondition? = null,
) {
    fun toCameraProfile(): CameraProfile {
        val effectNames = effects.map { it.name }
        return CameraProfile(
            id = id,
            displayName = name,
            era = category.toCompatibilityEra(),
            colorMatrix = parameters.toColorMatrix(),
            vignetteStrength = parameters.vignette,
            grainIntensity = parameters.grain,
            aspectRatio = captureHints.aspectRatio.toAspectRatio(),
            viewfinderType = captureHints.viewfinderType.toViewfinderType(),
            captureLatencyMs = captureHints.latencyMs,
            shaderPipeline = effects.toShaderPipeline(),
            chromaticAberration = parameters.chromaticAberration,
            flashBehavior = captureHints.flashBehavior.toFlashBehavior(),
            dateStampStyle = captureHints.dateStampStyle.toDateStampStyle(),
            crushedBlacks = parameters.crushedBlacks,
            shadowNoiseIntensity = parameters.shadowNoiseIntensity,
            interlacedPreview = effects.any {
                it == FilterEffect.VHS_SCANLINES || it == FilterEffect.VHS_DROPOUT || it == FilterEffect.GATE_WEAVE
            },
            previewVignetteAlpha = (parameters.vignette.coerceIn(0f, 1f) * 160f).toInt(),
            previewGrainIntensity = parameters.grain,
            deviceLabel = captureHints.deviceLabel.ifBlank { name },
            categoryLabel = category.displayName(),
            tierLabel = tier.name,
            assetStatusLabel = assets.assetStatus.name,
            effects = effectNames,
            lutAssetPath = assets.lut,
        )
    }
}

private fun FilterCategory.toCompatibilityEra(): Era = when (this) {
    FilterCategory.ERA_1980S -> Era.EIGHTIES
    FilterCategory.ERA_1990S, FilterCategory.FILM_STOCK -> Era.NINETIES
    FilterCategory.ERA_2000S, FilterCategory.ERA_2010S, FilterCategory.ERA_2020S, FilterCategory.ARTISTIC -> Era.TWO_THOUSANDS
}

private fun FilterCategory.displayName(): String = when (this) {
    FilterCategory.ERA_1980S -> "1980s"
    FilterCategory.ERA_1990S -> "1990s"
    FilterCategory.ERA_2000S -> "2000s"
    FilterCategory.ERA_2010S -> "2010s"
    FilterCategory.ERA_2020S -> "2020s"
    FilterCategory.FILM_STOCK -> "Film"
    FilterCategory.ARTISTIC -> "Art"
}

private fun String.toAspectRatio(): AspectRatio = runCatching { AspectRatio.valueOf(this) }
    .getOrDefault(AspectRatio.RATIO_4_3)

private fun String.toViewfinderType(): ViewfinderType = runCatching { ViewfinderType.valueOf(this) }
    .getOrDefault(ViewfinderType.OPTICAL)

private fun String.toFlashBehavior(): FlashBehavior = runCatching { FlashBehavior.valueOf(this) }
    .getOrDefault(FlashBehavior.OFF)

private fun String.toDateStampStyle(): DateStampStyle = runCatching { DateStampStyle.valueOf(this) }
    .getOrDefault(DateStampStyle.NONE)

private fun List<FilterEffect>.toShaderPipeline(): List<ShaderType> {
    val shaders = linkedSetOf<ShaderType>()
    if (any { it == FilterEffect.MASTER_GRADE || it == FilterEffect.LUT_3D }) shaders += ShaderType.COLOR_MATRIX
    if (any { it == FilterEffect.BLUE_NOISE_GRAIN }) shaders += ShaderType.GRAIN
    if (any { it == FilterEffect.VHS_SCANLINES }) shaders += ShaderType.SCANLINES
    if (any { it == FilterEffect.DATE_STAMP }) shaders += ShaderType.DATE_STAMP
    if (any { it == FilterEffect.JPEG_BLOCKS }) shaders += ShaderType.SHARPEN
    if (any { it == FilterEffect.VHS_CHROMA_BLEED || it == FilterEffect.CORNER_BLUR }) shaders += ShaderType.CHROMATIC_ABERRATION
    if (isEmpty()) shaders += ShaderType.COLOR_MATRIX
    return shaders.toList()
}
