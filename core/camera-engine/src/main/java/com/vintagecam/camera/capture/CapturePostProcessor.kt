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

@Singleton
class CapturePostProcessor @Inject constructor() {

    private val random = java.util.Random()

    fun apply(input: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap {
        var result = input.copy(Bitmap.Config.ARGB_8888, true)

        result = applyColorMatrix(result, profile.colorMatrix)

        if (profile.vignetteStrength > 0.01f) {
            result = applyVignette(result, profile.vignetteStrength)
        }

        if (profile.grainIntensity > 0.01f) {
            result = applyGrain(result, profile.grainIntensity)
        }

        if (profile.interlacedPreview || profile.shaderPipeline.contains(ShaderType.SCANLINES)) {
            result = applyScanlines(result, 0.45f)
        }

        if (profile.dateStampStyle != DateStampStyle.NONE) {
            result = applyDateStamp(result, profile.dateStampStyle, capturedAtMillis)
        }

        return result
    }

    private fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Bitmap {
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

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(fullMatrix))
        }
        Canvas(output).drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycleSafe()
        return output
    }

    private fun applyVignette(bitmap: Bitmap, strength: Float): Bitmap {
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
            floatArrayOf(0.55f, 1f),
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

    private fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val totalPx = w * h

        val srcPixels = IntArray(totalPx)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dstPixels = IntArray(totalPx)

        for (i in 0 until totalPx) {
            val noise = ((random.nextFloat() - 0.5f) * intensity * 120f).toInt()
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

    private fun applyScanlines(bitmap: Bitmap, intensity: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint().apply {
            color = Color.BLACK
            alpha = (intensity * 255f).toInt().coerceIn(0, 255)
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

    private fun applyDateStamp(bitmap: Bitmap, style: DateStampStyle, timestamp: Long): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = height * 0.035f
            typeface = Typeface.MONOSPACE
            when (style) {
                DateStampStyle.YELLOW_CLASSIC -> {
                    color = Color.YELLOW
                    setShadowLayer(3f, 1f, 1f, Color.BLACK)
                }
                DateStampStyle.RED_LED -> {
                    color = Color.RED
                }
                DateStampStyle.WHITE_LCD -> {
                    color = Color.WHITE
                    setShadowLayer(2f, 0f, 0f, Color.BLACK)
                }
                else -> color = Color.WHITE
            }
        }

        val dateText = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(timestamp))
        canvas.drawText(dateText, width * 0.05f, height * 0.92f, textPaint)
        bitmap.recycleSafe()
        return output
    }

    private fun Bitmap.recycleSafe() {
        if (!isRecycled) recycle()
    }
}