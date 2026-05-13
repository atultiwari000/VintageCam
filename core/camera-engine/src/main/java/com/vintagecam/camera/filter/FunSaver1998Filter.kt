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
import kotlin.math.hypot

/**
 * FunSaver '98 (disposable_1998) — 1990s disposable camera look.
 *
 * Signature effects:
 *  - Heavy vignette (0.7f — dark corners from cheap plastic lens)
 *  - Strong green color cast (cheap film with green bias)
 *  - Crushed blacks (shadows clip to pure black)
 *  - High contrast (almost posterized look)
 *  - Chunky ISO 400 grain
 *  - Slight blur/softness (cheap plastic lens)
 *  - Yellow date stamp "1998.05.11" with black outline
 *  - Light leak: warm orange gradient from top-right corner
 */
class FunSaver1998Filter : CameraFilter {

    override val profileId: String = "disposable_1998"

    private val random = java.util.Random()

    override fun apply(bitmap: Bitmap, profile: CameraProfile, timestamp: Long): Bitmap {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // 1. Green color cast matrix
        result = applyGreenCast(result)

        // 2. High contrast + crushed blacks (in pixel space)
        result = applyCrushedBlacksAndContrast(result, blackClip = 35, contrastBoost = 1.6f)

        // 3. Heavy vignette
        result = applyVignette(result, strength = 0.70f, gradientStart = 0.30f)

        // 4. Chunky ISO 400 grain
        result = applyGrain(result, intensity = 0.55f)

        // 5. Slight blur (cheap plastic lens — box blur)
        result = applyBoxBlur(result, radius = 1)

        // 6. Light leak from top-right corner
        result = applyLightLeak(result)

        // 7. Yellow date stamp with black outline
        result = applyDateStamp(result, timestamp)

        return result
    }

    // ── Green cast ───────────────────────────────────────────────────────

    private fun applyGreenCast(bitmap: Bitmap): Bitmap {
        val matrix = ColorMatrix(floatArrayOf(
            // R: pull down
            0.80f,  0.10f,  0.00f,  0f, -0.03f,
            // G: strong green boost
            0.00f,  1.45f,  0.10f,  0f, -0.02f,
            // B: pull blue way down (green bias)
            0.00f,  0.15f,  0.70f,  0f,  0.03f,
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

    // ── Crushed blacks + high contrast ──────────────────────────────────

    private fun applyCrushedBlacksAndContrast(bitmap: Bitmap, blackClip: Int, contrastBoost: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val totalPx = w * h

        val srcPixels = IntArray(totalPx)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val dstPixels = IntArray(totalPx)

        // Contrast pivot at 128
        val pivot = 128f

        for (i in 0 until totalPx) {
            val p = srcPixels[i]

            var r = Color.red(p)
            var g = Color.green(p)
            var b = Color.blue(p)

            // Crushed blacks: clip shadows
            r = if (r < blackClip) 0 else r
            g = if (g < blackClip) 0 else g
            b = if (b < blackClip) 0 else b

            // High contrast (S-curve style)
            r = ((r - pivot) * contrastBoost + pivot).toInt().coerceIn(0, 255)
            g = ((g - pivot) * contrastBoost + pivot).toInt().coerceIn(0, 255)
            b = ((b - pivot) * contrastBoost + pivot).toInt().coerceIn(0, 255)

            dstPixels[i] = Color.rgb(r, g, b)
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Vignette (heavy) ────────────────────────────────────────────────

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
            val noise = ((random.nextFloat() - 0.5f) * intensity * 160f).toInt()
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

    // ── Box blur ─────────────────────────────────────────────────────────

    private fun applyBoxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dst = IntArray(w * h)

        val diameter = radius * 2 + 1

        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (kx in -radius..radius) {
                    val sx = (x + kx).coerceIn(0, w - 1)
                    val p = pixels[y * w + sx]
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                    count++
                }
                dst[y * w + x] = Color.rgb(
                    (r / count).coerceIn(0, 255),
                    (g / count).coerceIn(0, 255),
                    (b / count).coerceIn(0, 255),
                )
            }
        }

        // Vertical pass
        val temp = dst.clone()
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (ky in -radius..radius) {
                    val sy = (y + ky).coerceIn(0, h - 1)
                    val p = temp[sy * w + x]
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                    count++
                }
                dst[y * w + x] = Color.rgb(
                    (r / count).coerceIn(0, 255),
                    (g / count).coerceIn(0, 255),
                    (b / count).coerceIn(0, 255),
                )
            }
        }

        output.setPixels(dst, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Light leak ───────────────────────────────────────────────────────

    private fun applyLightLeak(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val totalPx = w * h

        val srcPixels = IntArray(totalPx)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val dstPixels = IntArray(totalPx)

        // Warm orange light leak from top-right corner
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val p = srcPixels[idx]

                // Distance from top-right corner
                val dx = (w - x).toFloat() / w
                val dy = y.toFloat() / h
                val dist = kotlin.math.sqrt(dx * dx + dy * dy.toDouble()).toFloat()
                val leakStrength = ((1f - dist) * 0.35f).coerceIn(0f, 0.35f)

                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                // Warm orange: add red + green, keep blue suppressed
                val newR = (r + leakStrength * 120f).toInt().coerceIn(0, 255)
                val newG = (g + leakStrength * 40f).toInt().coerceIn(0, 255)
                val newB = (b - leakStrength * 30f).toInt().coerceIn(0, 255)

                dstPixels[idx] = Color.rgb(newR, newG, newB)
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Date stamp ───────────────────────────────────────────────────────

    private fun applyDateStamp(bitmap: Bitmap, timestamp: Long): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = height * 0.035f
            typeface = Typeface.MONOSPACE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = height * 0.035f
            typeface = Typeface.MONOSPACE
            this.color = Color.YELLOW
        }

        val dateText = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(timestamp))
        val textX = width * 0.05f
        val textY = height * 0.92f

        // Draw black outline manually
        canvas.drawText(dateText, textX - 1.5f, textY - 1.5f, shadowPaint)
        canvas.drawText(dateText, textX + 1.5f, textY - 1.5f, shadowPaint)
        canvas.drawText(dateText, textX - 1.5f, textY + 1.5f, shadowPaint)
        canvas.drawText(dateText, textX + 1.5f, textY + 1.5f, shadowPaint)

        // Draw yellow text on top
        canvas.drawText(dateText, textX, textY, textPaint)
        bitmap.recycleSafe()
        return output
    }

    private fun Bitmap.recycleSafe() {
        if (!isRecycled) recycle()
    }
}
