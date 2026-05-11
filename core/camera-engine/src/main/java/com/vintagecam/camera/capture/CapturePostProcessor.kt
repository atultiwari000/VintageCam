package com.vintagecam.camera.capture

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
import com.vintagecam.profiles.DateStampStyle
import com.vintagecam.profiles.ShaderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

/**
 * Applies vintage camera effects to captured bitmaps.
 *
 * CRITICAL RULE: Every filter creates a FRESH output bitmap and draws
 * the SOURCE onto it. Never create [Canvas] backed by the same bitmap
 * you are reading from — hardware-accelerated Canvas can silently drop
 * draw calls when source and destination share backing memory.
 */
@Singleton
class CapturePostProcessor @Inject constructor() {

    fun apply(input: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap {
        // Always start from a fresh mutable ARGB_8888 copy so the original
        // input bitmap (which may come from CameraX JPEG/YUV) is never mutated.
        var result = input.copy(Bitmap.Config.ARGB_8888, true)

        // 1 — Color matrix (tone adjustment)
        result = applyColorMatrix(result, profile.colorMatrix)

        // 2 — Vignette (radial darkening at edges)
        if (profile.vignetteStrength > 0f) {
            result = applyVignette(result, profile.vignetteStrength)
        }

        // 3 — Film grain (per-pixel luminance noise)
        if (profile.grainIntensity > 0f) {
            result = applyGrain(result, profile.grainIntensity)
        }

        // 3b — Scanlines (for CRT / VHS profiles that have interlaced preview)
        if (profile.interlacedPreview || profile.shaderPipeline.contains(ShaderType.SCANLINES)) {
            result = applyScanlines(result, 0.3f)
        }

        // 4 — Date stamp overlay
        if (profile.dateStampStyle != DateStampStyle.NONE) {
            result = applyDateStamp(result, profile.dateStampStyle, capturedAtMillis)
        }

        return result
    }

    // ── Color Matrix ────────────────────────────────────────────────

    private fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Bitmap {
        // ProfileRepository stores 9-element (3×3) RGB matrices.
        // ColorMatrix(float[]) requires a 20-element (4×5) row-major array.
        val fullMatrix = if (matrix.size == 9) {
            floatArrayOf(
                matrix[0], matrix[1], matrix[2], 0f, 0f,
                matrix[3], matrix[4], matrix[5], 0f, 0f,
                matrix[6], matrix[7], matrix[8], 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        } else {
            matrix
        }

        // Fresh mutable output — Canvas source ≠ Canvas backing.
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(fullMatrix))
        }

        Canvas(output).apply {
            drawBitmap(bitmap, 0f, 0f, paint)
        }

        return output
    }

    // ── Vignette ────────────────────────────────────────────────────

    private fun applyVignette(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = hypot(centerX.toDouble(), centerY.toDouble()).toFloat()

        // Fresh output — start blank, draw source onto it.
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)

        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Then draw the vignette gradient ON TOP.
        val gradient = RadialGradient(
            centerX, centerY, maxRadius,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.7f, 1f),
            Shader.TileMode.CLAMP,
        )

        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            alpha = (strength * 255).toInt().coerceIn(0, 255)
        }

        canvas.drawRect(0f, 0f, width, height, vignettePaint)
        return output
    }

    // ── Grain ───────────────────────────────────────────────────────

    private fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val totalPx = w * h

        // Read source pixels.
        val srcPixels = IntArray(totalPx)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        // Fresh mutable output — start blank.
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val dstPixels = IntArray(totalPx)

        val random = java.util.Random()
        for (i in 0 until totalPx) {
            val noise = ((random.nextFloat() - 0.5f) * intensity * 255f).toInt()
            val p = srcPixels[i]
            dstPixels[i] = Color.rgb(
                (Color.red(p) + noise).coerceIn(0, 255),
                (Color.green(p) + noise).coerceIn(0, 255),
                (Color.blue(p) + noise).coerceIn(0, 255),
            )
        }

        output.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return output
    }

    // ── Date Stamp ──────────────────────────────────────────────────

    private fun applyDateStamp(
        bitmap: Bitmap,
        style: DateStampStyle,
        timestamp: Long,
    ): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        // Fresh output — start blank, draw source onto it.
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)

        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Then draw the date text ON TOP.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = height * 0.03f
            typeface = Typeface.MONOSPACE
            when (style) {
                DateStampStyle.YELLOW_CLASSIC -> {
                    color = Color.YELLOW
                    setShadowLayer(2f, 1f, 1f, Color.BLACK)
                }
                DateStampStyle.RED_LED -> {
                    color = Color.RED
                }
                DateStampStyle.WHITE_LCD -> {
                    color = Color.WHITE
                    setShadowLayer(1f, 0f, 0f, Color.BLACK)
                }
                else -> color = Color.WHITE
            }
        }

        val dateText = SimpleDateFormat("yyyy.MM.dd", Locale.US)
            .format(Date(timestamp))

        canvas.drawText(
            dateText,
            width * 0.05f,
            height * 0.95f,
            textPaint,
        )

        return output
    }

    // ── Scanlines ─────────────────────────────────────────────────

    private fun applyScanlines(bitmap: Bitmap, intensity: Float): Bitmap {
        val newBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint().apply {
            color = Color.BLACK
            alpha = (intensity * 0.3f * 255f).toInt().coerceIn(0, 255)
            strokeWidth = 2f
        }

        val lineHeight = 4f
        var y = 0f
        while (y < bitmap.height) {
            canvas.drawLine(0f, y, bitmap.width.toFloat(), y, paint)
            y += lineHeight
        }

        bitmap.recycle()
        return newBitmap
    }
}
