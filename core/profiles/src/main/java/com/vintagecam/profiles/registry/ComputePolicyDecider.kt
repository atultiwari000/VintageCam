package com.vintagecam.profiles.registry

internal data class ComputePolicy(
    val mode: String,
    val frames: Int,
    val noiseReduction: Float,
    val toneRecovery: Float,
    val portraitEnhancement: Float,
) {
    companion object {
        fun low(): ComputePolicy = ComputePolicy(
            mode = "SINGLE",
            frames = 1,
            noiseReduction = 0f,
            toneRecovery = 0f,
            portraitEnhancement = 0f,
        )
    }
}

internal object ComputePolicyDecider {

    fun decide(
        presetId: String,
        category: FilterCategory,
        effects: List<FilterEffect>,
    ): ComputePolicy {
        if (presetId in lowComputeIds || effects.any {
                it == FilterEffect.VHS_SCANLINES ||
                    it == FilterEffect.VHS_DROPOUT ||
                    it == FilterEffect.GLITCH_SLICES ||
                    it == FilterEffect.JPEG_BLOCKS
            }
        ) {
            return ComputePolicy.low()
        }

        if (presetId in heavyPortraitIds) {
            return ComputePolicy(
                mode = "BURST_PORTRAIT",
                frames = 5,
                noiseReduction = 0.44f,
                toneRecovery = 0.30f,
                portraitEnhancement = 0.24f,
            )
        }

        if (presetId in heavyHdrIds) {
            return ComputePolicy(
                mode = "BURST_HDR",
                frames = 5,
                noiseReduction = 0.52f,
                toneRecovery = 0.44f,
                portraitEnhancement = 0.12f,
            )
        }

        if (category == FilterCategory.FILM_STOCK && effects.contains(FilterEffect.BLUE_NOISE_GRAIN)) {
            return ComputePolicy(
                mode = "BURST_NOSTALGIA",
                frames = 4,
                noiseReduction = 0.32f,
                toneRecovery = 0.22f,
                portraitEnhancement = 0.12f,
            )
        }

        return ComputePolicy.low()
    }

    private val lowComputeIds = setOf(
        "vhs_1985",
        "disposable_1998",
        "digicam_2003",
        "instagram_matte_2010",
        "teal_orange_2020",
        "moody_dark_2020",
        "toy_camera_2000",
        "lomography_1995",
        "cross_process",
        "bleach_bypass",
        "duotone",
        "glitch",
        "cyanotype",
        "infrared",
    )

    private val heavyPortraitIds = setOf(
        "portra_400",
        "fuji_pro_400h",
        "golden_hour_2010",
        "polaroid_1990",
        "expired_instant",
    )

    private val heavyHdrIds = setOf(
        "cinestill_800t",
        "neopan_1600",
        "super8_2020",
        "kodachrome_1980",
    )
}