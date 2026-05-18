package com.vintagecam.camera.capture

import android.graphics.Bitmap
import com.vintagecam.profiles.CameraProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * First-pass computational capture path.
 *
 * This deliberately starts with a motion-tolerant weighted merge instead of
 * full alignment. It gives selected filters cleaner source material now, while
 * leaving room for native alignment/HDR to replace the internals later.
 */
internal object ComputationalBurstProcessor {

    fun begin(reference: Bitmap, profile: CameraProfile, totalFrames: Int): Accumulator {
        return Accumulator(reference, profile, totalFrames.coerceIn(2, 8))
    }

    class Accumulator internal constructor(
        reference: Bitmap,
        private val profile: CameraProfile,
        private val totalFrames: Int,
    ) {
        private val width = reference.width
        private val height = reference.height
        private val mergedPixels = IntArray(width * height)
        private val framePixels = IntArray(width * height)

        private val noiseReduction = profile.noiseReductionStrength.coerceIn(0f, 1f)
        private val toneRecovery = profile.toneRecoveryStrength.coerceIn(0f, 1f)
        private val portraitEnhancement = profile.portraitEnhancementStrength.coerceIn(0f, 1f)

        init {
            reference.getPixels(mergedPixels, 0, width, 0, 0, width, height)
        }

        fun addFrame(frame: Bitmap) {
            if (frame.width != width || frame.height != height) return

            frame.getPixels(framePixels, 0, width, 0, 0, width, height)
            val baseBlend = (noiseReduction / (totalFrames - 1).coerceAtLeast(1)).coerceIn(0f, 0.32f)
            if (baseBlend <= 0f) return

            for (i in mergedPixels.indices) {
                val base = mergedPixels[i]
                val sample = framePixels[i]
                val motionWeight = motionWeight(base, sample)
                val blend = baseBlend * motionWeight * motionWeight
                if (blend <= 0.002f) continue

                val a = base ushr 24
                val r = blendChannel(base.red, sample.red, blend)
                val g = blendChannel(base.green, sample.green, blend)
                val b = blendChannel(base.blue, sample.blue, blend)
                mergedPixels[i] = argb(a, r, g, b)
            }
        }

        fun finish(): Bitmap {
            if (toneRecovery > 0f || portraitEnhancement > 0f) {
                applyQualityPass()
            }

            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(mergedPixels, 0, width, 0, 0, width, height)
            }
        }

        private fun applyQualityPass() {
            for (i in mergedPixels.indices) {
                val pixel = mergedPixels[i]
                val a = pixel ushr 24
                var r = pixel.red
                var g = pixel.green
                var b = pixel.blue

                if (toneRecovery > 0f) {
                    r = toneMap(r, toneRecovery)
                    g = toneMap(g, toneRecovery)
                    b = toneMap(b, toneRecovery)
                }

                if (portraitEnhancement > 0f && isSkinLike(r, g, b)) {
                    val luma = luma(r, g, b)
                    val satScale = 1f - portraitEnhancement * 0.055f
                    r = (luma + (r - luma) * satScale + (255f - r) * portraitEnhancement * 0.018f).toInt().coerceIn(0, 255)
                    g = (luma + (g - luma) * satScale + (255f - g) * portraitEnhancement * 0.022f).toInt().coerceIn(0, 255)
                    b = (luma + (b - luma) * satScale + (255f - b) * portraitEnhancement * 0.014f).toInt().coerceIn(0, 255)
                }

                mergedPixels[i] = argb(a, r, g, b)
            }
        }
    }

    private fun motionWeight(base: Int, sample: Int): Float {
        val delta = abs(luma(base.red, base.green, base.blue) - luma(sample.red, sample.green, sample.blue)) / 255f
        return when {
            delta <= 0.08f -> 1f
            delta >= 0.30f -> 0f
            else -> 1f - ((delta - 0.08f) / 0.22f)
        }
    }

    private fun blendChannel(base: Int, sample: Int, amount: Float): Int {
        return (base + (sample - base) * amount).toInt().coerceIn(0, 255)
    }

    private fun toneMap(value: Int, strength: Float): Int {
        var x = value / 255f
        if (x < 0.42f) {
            x += (0.42f - x) * 0.10f * strength
        }
        if (x > 0.72f) {
            val over = x - 0.72f
            x -= over * over * 0.55f * strength
        }
        return (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun isSkinLike(r: Int, g: Int, b: Int): Boolean {
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val y = luma(r, g, b)
        return y > 44f &&
            maxChannel - minChannel > 12 &&
            r > b &&
            r >= g * 0.78f &&
            g >= b * 0.72f
    }

    private fun luma(r: Int, g: Int, b: Int): Float = r * 0.299f + g * 0.587f + b * 0.114f

    private val Int.red: Int get() = (this shr 16) and 0xFF
    private val Int.green: Int get() = (this shr 8) and 0xFF
    private val Int.blue: Int get() = this and 0xFF

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
