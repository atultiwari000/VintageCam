package com.vintagecam.profiles.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class ComputePolicyDeciderTest {

    @Test
    fun `vhs style effects stay low compute`() {
        val policy = ComputePolicyDecider.decide(
            presetId = "custom_vhs_like",
            category = FilterCategory.ARTISTIC,
            effects = listOf(FilterEffect.MASTER_GRADE, FilterEffect.VHS_SCANLINES),
        )

        assertEquals("SINGLE", policy.mode)
        assertEquals(1, policy.frames)
        assertEquals(0f, policy.noiseReduction)
        assertEquals(0f, policy.toneRecovery)
        assertEquals(0f, policy.portraitEnhancement)
    }

    @Test
    fun `portrait presets use heavy portrait burst`() {
        val policy = ComputePolicyDecider.decide(
            presetId = "portra_400",
            category = FilterCategory.FILM_STOCK,
            effects = listOf(FilterEffect.MASTER_GRADE, FilterEffect.BLUE_NOISE_GRAIN),
        )

        assertEquals("BURST_PORTRAIT", policy.mode)
        assertEquals(5, policy.frames)
        assertEquals(0.44f, policy.noiseReduction)
        assertEquals(0.30f, policy.toneRecovery)
        assertEquals(0.24f, policy.portraitEnhancement)
    }

    @Test
    fun `hdr presets use heavy hdr burst`() {
        val policy = ComputePolicyDecider.decide(
            presetId = "cinestill_800t",
            category = FilterCategory.FILM_STOCK,
            effects = listOf(FilterEffect.MASTER_GRADE, FilterEffect.HALATION),
        )

        assertEquals("BURST_HDR", policy.mode)
        assertEquals(5, policy.frames)
        assertEquals(0.52f, policy.noiseReduction)
        assertEquals(0.44f, policy.toneRecovery)
        assertEquals(0.12f, policy.portraitEnhancement)
    }

    @Test
    fun `film stock defaults to nostalgia burst when grain present`() {
        val policy = ComputePolicyDecider.decide(
            presetId = "agfa_vista_200",
            category = FilterCategory.FILM_STOCK,
            effects = listOf(FilterEffect.MASTER_GRADE, FilterEffect.BLUE_NOISE_GRAIN),
        )

        assertEquals("BURST_NOSTALGIA", policy.mode)
        assertEquals(4, policy.frames)
        assertEquals(0.32f, policy.noiseReduction)
        assertEquals(0.22f, policy.toneRecovery)
        assertEquals(0.12f, policy.portraitEnhancement)
    }

    @Test
    fun `non listed modern look defaults to low compute`() {
        val policy = ComputePolicyDecider.decide(
            presetId = "clean_modern",
            category = FilterCategory.ERA_2020S,
            effects = listOf(FilterEffect.MASTER_GRADE),
        )

        assertEquals("SINGLE", policy.mode)
        assertEquals(1, policy.frames)
    }
}
