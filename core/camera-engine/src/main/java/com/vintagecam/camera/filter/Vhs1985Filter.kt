package com.vintagecam.camera.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.vintagecam.profiles.CameraProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * VHS-C 1985 (VHS 1985) — 1980s camcorder look.
 *
 * Signature effects:
 *  - Heavy green-magenta color shift (magnetic tape degradation)
 *  - Strong chromatic aberration (RGB channel separation)
 *  - Aggressive scanlines (visible horizontal lines)
 *  - CRT-style barrel distortion (slight bulge in center)
 *  - Heavy vignette
 *  - Chunky visible film grain
 *  - Red monospace date stamp "1985.05.11"
 */
class Vhs1985Filter : CameraFilter {

    override val profileId: String = "vhs_1985"

    private val random = java.util.Random()

    override fun apply(bitmap: Bitmap, profile: CameraProfile, timestamp: Long): Bitmap {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // 1. Green-magenta color matrix shift (magnetic tape look)
        result = applyGreenMagentaShift(result)

        // 2. Strong chromatic aberration via channel offsets
        result = applyChromaticAberration(result, shiftPx = 5f)

        // 3. Aggressive scanlines
        result = applyScanlines(result, lineAlpha = 0.40f)

        // 4. Barrel distortion (slight bulge — uses pixel sampling at center)
        result = applyBarrelDistortion(result, strength = 0.12f)

        // 5. Heavy vignette
        result = applyVignette(result, strength = 0.50f, gradientStart = 0.40f)

        // 6. Chunky visible grain
        result = applyGrain(result, intensity = 0.40f)

        // 7. Red LED date stamp
        result = applyDateStamp(result, timestamp, color = Color.RED, shadow = null)

        return result
    }

    // ── Green-magenta shift ──────────────────────────────────────────────

    private fun applyGreenMagentaShift(bitmap: Bitmap): Bitmap {
        val matrix = ColorMatrix(floatArrayOf(
            // R: pull red down, push green up
            0.70f,  0.30f,  0.00f,  0f, -0.05f,
            // G: heavy green boost
           -0.10f,  1.60f, -0.10f,  0f, -0.03f,
            // B: pull blue down (magenta cast)
            0.15f, -0.10f,  0.80f,  0f,  0.05f,
            0f,     0f,     0f,     1f,  0f,
        ))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycleSafe()
        return output
    }

    // ── Chromatic aberration ────────────────────────────────────────────

    private fun applyChromaticAberration(bitmap: Bitmap, shiftPx: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x

                // Red channel shifted right
                val rx = (x - shiftPx).roundToInt().coerceIn(0, w - 1)
                val ridx = y * w + rx

                // Blue channel shifted left
                val bx = (x + shiftPx).roundToInt().coerceIn(0, w - 1)
                val bidx = y * w + bx

                val src = pixels[idx]
                val rPixel = pixels[ridx]
                val bPixel = pixels[bidx]

                val r = Color.red(rPixel)
                val g = Color.green(src)
                val b = Color.blue(bPixel)

                outPixels[idx] = Color.rgb(r, g, b)
            }
        }

        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Scanlines ────────────────────────────────────────────────────────

    private fun applyScanlines(bitmap: Bitmap, lineAlpha: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint().apply {
            color = Color.BLACK
            alpha = (lineAlpha * 255f).toInt().coerceIn(0, 255)
            strokeWidth = 2f
        }

        val lineHeight = 4f
        var y = 0f
        while (y < bitmap.height) {
            canvas.drawLine(0f, y, bitmap.width.toFloat(), y + 1f, paint)
            y += lineHeight
        }

        bitmap.recycleSafe()
        return output
    }

    // ── Barrel distortion ────────────────────────────────────────────────

    private fun applyBarrelDistortion(bitmap: Bitmap, strength: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = hypot(cx.toDouble(), cy.toDouble())

        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dst = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val dist = hypot(dx.toDouble(), dy.toDouble())
                val norm = dist / maxDist

                // Barrel distortion: push pixels outward from center
                val factor = 1f + strength * norm * norm
                val srcX = (cx + dx / factor).roundToInt().coerceIn(0, w - 1)
                val srcY = (cy + dy / factor).roundToInt().coerceIn(0, h - 1)

                dst[y * w + x] = src[srcY * w + srcX]
            }
        }

        output.setPixels(dst, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Vignette ─────────────────────────────────────────────────────────

    private fun applyVignette(bitmap: Bitmap, strength: Float, gradientStart: Float): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = hypot(centerX.toDouble(), centerY.toDouble()).toFloat()

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val gradient = RadialGradient(
            centerX, centerY, maxRadius,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(gradientStart, 1f),
            Shader.TileMode.CLAMP,
        )

        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            alpha = (strength * 255).toInt().coerceIn(0, 255)
        }

        canvas.drawRect(0f, 0f, width, height, vignettePaint)
        bitmap.recycleSafe()
        return output
    }

    // ── Grain ────────────────────────────────────────────────────────────

    private fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val totalPx = w * h

        val srcPixels = IntArray(totalPx)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dstPixels = IntArray(totalPx)

        for (i in 0 until totalPx) {
            val noise = ((random.nextFloat() - 0.5f) * intensity * 140f).toInt()
            val p = srcPixels[i]
            dstPixels[i] = Color.rgb(
                (Color.red(p) + noise).coerceIn(0, 255),
                (Color.green(p) + noise).coerceIn(0, 255),
                (Color.blue(p) + noise).coerceIn(0, 255),
            )
        }

        output.setPixels(dstPixels, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Date stamp ───────────────────────────────────────────────────────

    private fun applyDateStamp(bitmap: Bitmap, timestamp: Long, color: Int, shadow: Int?): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = height * 0.038f
            typeface = Typeface.MONOSPACE
            this.color = color
            if (shadow != null) {
                setShadowLayer(3f, 1f, 1f, shadow)
            }
        }

        val dateText = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(timestamp))
        canvas.drawText(dateText, width * 0.04f, height * 0.93f, textPaint)
        bitmap.recycleSafe()
        return output
    }

    private fun Bitmap.recycleSafe() {
        if (!isRecycled) recycle()
    }
}
