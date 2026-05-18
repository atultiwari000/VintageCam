package com.vintagecam.profiles.registry

data class FilterParameters(
    val grain: Float = 0f,
    val vignette: Float = 0f,
    val vignetteMid: Float = 0.45f,
    val vignetteOuter: Float = 0.85f,
    val fade: Float = 0f,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val halation: Float = 0f,
    val chromaticAberration: Float = 0f,
    val barrel: Float = 0f,
    val tintShadow: FloatArray = floatArrayOf(0f, 0f, 0f),
    val tintHighlight: FloatArray = floatArrayOf(0f, 0f, 0f),
    val lutStrength: Float = 0f,
    val grainSize: GrainSize = GrainSize.MEDIUM,
    val crushedBlacks: Float = 0f,
    val shadowNoiseIntensity: Float = 0f,
) {
    enum class GrainSize {
        FINE,
        MEDIUM,
        COARSE,
        DIGITAL,
        TAPE,
    }

    fun toColorMatrix(): FloatArray {
        val sat = saturation.coerceIn(0f, 1.8f)
        val invSat = 1f - sat
        val r = 0.213f * invSat
        val g = 0.715f * invSat
        val b = 0.072f * invSat
        val contrastScale = contrast.coerceIn(0.5f, 1.8f)
        val translate = ((1f - contrastScale) * 128f) + (fade.coerceIn(0f, 0.4f) * 255f)

        return floatArrayOf(
            (r + sat) * contrastScale + tintHighlight[0], g * contrastScale, b * contrastScale + tintShadow[2], 0f, translate + tintHighlight[0] * 64f,
            r * contrastScale, (g + sat) * contrastScale + tintShadow[1], b * contrastScale, 0f, translate + tintHighlight[1] * 64f,
            r * contrastScale + tintShadow[0], g * contrastScale, (b + sat) * contrastScale + tintHighlight[2], 0f, translate + tintHighlight[2] * 64f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
