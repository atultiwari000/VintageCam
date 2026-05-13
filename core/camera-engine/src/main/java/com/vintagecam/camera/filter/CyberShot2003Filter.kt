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
import kotlin.math.roundToInt

/**
 * CyberShot 2003 (digicam_2003) — early 2000s digital camera look.
 *
 * Signature effects:
 *  - Oversharpened edges (convolution kernel)
 *  - CCD color response: boosted reds + greens, cool shadows
 *  - Digital chroma noise in shadows (not film grain)
 *  - Slight vignette (mild lens shading)
 *  - High saturation (early digital oversaturation)
 *  - White date stamp "2003.05.11" small + minimal
 *  - Slight blue tint in shadows (early CMOS/CCD characteristic)
 */
class CyberShot2003Filter : CameraFilter {

    override val profileId: String = "digicam_2003"

    private val random = java.util.Random()

    override fun apply(bitmap: Bitmap, profile: CameraProfile, timestamp: Long): Bitmap {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // 1. CCD color response + high saturation + cool shadow blue shift
        result = applyCcdColorMatrix(result)

        // 2. Oversharpened edges (Sharpen convolution kernel)
        result = applySharpen(result, strength = 0.50f)

        // 3. Digital chroma noise in shadows
        result = applyChromaNoise(result, intensity = 0.25f)

        // 4. Slight vignette
        result = applyVignette(result, strength = 0.15f, gradientStart = 0.65f)

        // 5. White minimal date stamp
        result = applyDateStamp(result, timestamp)

        return result
    }

    // ── CCD color matrix ────────────────────────────────────────────────

    private fun applyCcdColorMatrix(bitmap: Bitmap): Bitmap {
        val matrix = ColorMatrix(floatArrayOf(
            // R: boosted reds
            1.50f, -0.05f, -0.05f,  0f, -0.02f,
            // G: boosted greens
           -0.10f,  1.40f, -0.05f,  0f, -0.02f,
            // B: slightly cool shadows (raised blue channel)
           -0.05f, -0.10f,  1.30f,  0f,  0.04f,
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

    // ── Sharpen convolution ─────────────────────────────────────────────

    private fun applySharpen(bitmap: Bitmap, strength: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dst = IntArray(w * h)

        // Sharpen kernel
        val kernel = floatArrayOf(
             0f, -strength,  0f,
            -strength,  1f + 4f * strength, -strength,
             0f, -strength,  0f,
        )

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var r = 0f; var g = 0f; var b = 0f
                var ki = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val p = pixels[(y + ky) * w + (x + kx)]
                        val k = kernel[ki++]
                        r += Color.red(p) * k
                        g += Color.green(p) * k
                        b += Color.blue(p) * k
                    }
                }
                dst[y * w + x] = Color.rgb(
                    r.roundToInt().coerceIn(0, 255),
                    g.roundToInt().coerceIn(0, 255),
                    b.roundToInt().coerceIn(0, 255),
                )
            }
        }

        // Copy edges from original (unsharpened)
        for (x in 0 until w) {
            dst[x] = pixels[x]                               // top edge
            dst[(h - 1) * w + x] = pixels[(h - 1) * w + x]   // bottom edge
        }
        for (y in 0 until h) {
            dst[y * w] = pixels[y * w]                               // left edge
            dst[y * w + (w - 1)] = pixels[y * w + (w - 1)]           // right edge
        }

        output.setPixels(dst, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Digital chroma noise in shadows ────────────────────────────────

    private fun applyChromaNoise(bitmap: Bitmap, intensity: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val totalPx = w * h

        val srcPixels = IntArray(totalPx)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val dstPixels = IntArray(totalPx)

        for (i in 0 until totalPx) {
            val p = srcPixels[i]
            val luminance = (Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f)

            // Only add chroma noise to darker pixels
            if (luminance < 100f) {
                // Add noise only to color channels (chroma), not luminance
                val noiseR = ((random.nextFloat() - 0.5f) * intensity * 80f).toInt()
                val noiseB = ((random.nextFloat() - 0.5f) * intensity * 80f).toInt()

                dstPixels[i] = Color.rgb(
                    (Color.red(p) + noiseR).coerceIn(0, 255),
                    Color.green(p), // keep green clean
                    (Color.blue(p) + noiseB).coerceIn(0, 255),
                )
            } else {
                dstPixels[i] = p
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, w, 0, 0, w, h)
        bitmap.recycleSafe()
        return output
    }

    // ── Vignette (subtle) ──────────────────────────────────────────────

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

    // ── Date stamp ─────────────────────────────────────────────────────

    private fun applyDateStamp(bitmap: Bitmap, timestamp: Long): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Small, thin, minimal white text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = height * 0.025f
            typeface = Typeface.MONOSPACE
            this.color = Color.WHITE
            alpha = 180 // slightly transparent
        }

        val dateText = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(timestamp))
        canvas.drawText(dateText, width * 0.04f, height * 0.95f, textPaint)
        bitmap.recycleSafe()
        return output
    }

    private fun Bitmap.recycleSafe() {
        if (!isRecycled) recycle()
    }
}
