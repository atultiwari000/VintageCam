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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

@Singleton
class CapturePostProcessor @Inject constructor() {

    fun apply(input: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap {
        var result = input.copy(Bitmap.Config.ARGB_8888, true)

        // Apply color matrix
        result = applyColorMatrix(result, profile.colorMatrix)

        // Apply vignette
        if (profile.vignetteStrength > 0) {
            result = applyVignette(result, profile.vignetteStrength)
        }

        // Apply grain (simplified - just noise overlay)
        if (profile.grainIntensity > 0) {
            result = applyGrain(result, profile.grainIntensity)
        }

        // Apply date stamp
        if (profile.dateStampStyle != DateStampStyle.NONE) {
            result = applyDateStamp(result, profile.dateStampStyle, capturedAtMillis)
        }

        return result
    }

    private fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Bitmap {
        // Profile stores a full 20-element 4x5 Android ColorMatrix.
        // No conversion needed — pass directly to ColorMatrixColorFilter.
        val resultBitmap = bitmap.copy(bitmap.config, true)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return resultBitmap
    }

    private fun applyVignette(bitmap: Bitmap, strength: Float): Bitmap {
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val maxRadius = hypot(centerX, centerY)

        val paint = Paint().apply {
            shader = RadialGradient(
                centerX, centerY, maxRadius,
                intArrayOf(Color.TRANSPARENT, Color.BLACK),
                floatArrayOf(0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            alpha = (strength * 255).toInt()
        }

        canvas.drawRect(0f, 0f, width, height, paint)
        return bitmap
    }

    private fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val random = java.util.Random()
        for (i in pixels.indices) {
            val noise = (random.nextFloat() - 0.5f) * intensity * 255
            val pixel = pixels[i]
            val r = (Color.red(pixel) + noise).toInt().coerceIn(0, 255)
            val g = (Color.green(pixel) + noise).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixel) + noise).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(r, g, b)
        }

        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return bitmap
    }

    private fun applyDateStamp(bitmap: Bitmap, style: DateStampStyle, timestamp: Long): Bitmap {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            textSize = bitmap.height * 0.03f
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
        canvas.drawText(dateText, bitmap.width * 0.05f, bitmap.height * 0.95f, paint)

        return bitmap
    }
}
