package com.vintagecam.camera.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.DateStampStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

@Singleton
class CapturePostProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lutCache = mutableMapOf<String, CubeLut?>()

    fun apply(input: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap {
        var result = input.copy(Bitmap.Config.ARGB_8888, true)

        // Apply color matrix
        result = applyColorMatrix(result, profile.colorMatrix)

        result = applyLutIfAvailable(result, profile)

        // Apply vignette
        if (profile.vignetteStrength > 0) {
            result = applyVignette(result, profile.vignetteStrength)
        }

        // Apply grain (simplified - just noise overlay)
        if (profile.grainIntensity > 0) {
            result = applyGrain(result, profile.grainIntensity)
        }

        result = applySpecialEffects(result, profile)

        // Apply date stamp
        if (profile.dateStampStyle != DateStampStyle.NONE) {
            result = applyDateStamp(result, profile.dateStampStyle, capturedAtMillis)
        }

        return result
    }

    private fun applyLutIfAvailable(bitmap: Bitmap, profile: CameraProfile): Bitmap {
        val path = profile.lutAssetPath ?: return bitmap
        val lut = lutCache.getOrPut(path) {
            runCatching {
                context.assets.open(path).use { CubeLut.parse(it) }
            }.getOrNull().also {
                if (it == null) {
                    android.util.Log.w("CapturePostProcessor", "LUT unavailable or invalid for ${profile.id}: $path")
                }
            }
        } ?: return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        lut.applyTo(pixels, if ("LUT_3D" in profile.effects) 1f else 0.55f)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Bitmap {
        // Profile stores a full 20-element 4x5 Android ColorMatrix.
        // No conversion needed — pass directly to ColorMatrixColorFilter.
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
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

    private fun applySpecialEffects(bitmap: Bitmap, profile: CameraProfile): Bitmap {
        var result = bitmap
        val effects = profile.effects.toSet()

        if ("FLASH_FALLOFF" in effects) result = applyFlashFalloff(result)
        if ("LIGHT_LEAK" in effects) result = applyLightLeak(result)
        if ("HALATION" in effects) result = applyWarmHalation(result, profile.grainIntensity)
        if ("VHS_SCANLINES" in effects) result = applyScanlines(result)
        if ("VHS_CHROMA_BLEED" in effects) result = applyChromaBleed(result)
        if ("JPEG_BLOCKS" in effects) result = applyJpegBlocks(result)
        if ("GLITCH_SLICES" in effects) result = applyGlitchSlices(result)
        if ("DUOTONE" in effects || "CYANOTYPE_PAPER" in effects) result = applyDuotone(result, profile)
        if ("FRAME_OVERLAY" in effects) result = applyFrameOverlay(result, profile)

        return result
    }

    private fun applyFlashFalloff(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        val radius = kotlin.math.max(bitmap.width, bitmap.height) * 0.62f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bitmap.width * 0.52f,
                bitmap.height * 0.44f,
                radius,
                intArrayOf(Color.argb(54, 255, 242, 210), Color.TRANSPARENT, Color.argb(64, 0, 0, 0)),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        return bitmap
    }

    private fun applyLightLeak(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bitmap.width * 0.96f,
                bitmap.height * 0.06f,
                bitmap.width * 0.55f,
                intArrayOf(Color.argb(128, 255, 90, 28), Color.argb(48, 255, 210, 62), Color.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        paint.xfermode = null
        return bitmap
    }

    private fun applyWarmHalation(bitmap: Bitmap, strength: Float): Bitmap {
        val canvas = Canvas(bitmap)
        val alpha = ((strength.coerceIn(0.05f, 0.7f)) * 70f).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bitmap.width * 0.50f,
                bitmap.height * 0.35f,
                bitmap.width * 0.75f,
                intArrayOf(Color.argb(alpha, 255, 130, 54), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        paint.xfermode = null
        return bitmap
    }

    private fun applyScanlines(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(38, 0, 0, 0)
            strokeWidth = kotlin.math.max(1f, bitmap.height / 900f)
        }
        val spacing = kotlin.math.max(3f, bitmap.height / 240f)
        var y = 0f
        while (y < bitmap.height) {
            canvas.drawLine(0f, y, bitmap.width.toFloat(), y, paint)
            y += spacing
        }
        return bitmap
    }

    private fun applyChromaBleed(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = IntArray(width * height)
        val dst = IntArray(width * height)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)

        val offset = (width * 0.006f).toInt().coerceAtLeast(1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val center = src[y * width + x]
                val left = src[y * width + (x - offset).coerceIn(0, width - 1)]
                val right = src[y * width + (x + offset).coerceIn(0, width - 1)]
                dst[y * width + x] = Color.rgb(
                    Color.red(right),
                    Color.green(center),
                    Color.blue(left),
                )
            }
        }

        bitmap.setPixels(dst, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun applyJpegBlocks(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        val block = kotlin.math.max(8, bitmap.width / 90)
        val paint = Paint().apply {
            color = Color.argb(14, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (((x / block) + (y / block)) % 3 == 0) {
                    canvas.drawRect(
                        Rect(x, y, (x + block).coerceAtMost(bitmap.width), (y + block).coerceAtMost(bitmap.height)),
                        paint,
                    )
                }
                x += block
            }
            y += block
        }
        return bitmap
    }

    private fun applyGlitchSlices(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val canvas = Canvas(bitmap)
        val random = java.util.Random((width * 31L) + height)
        repeat(10) {
            val sliceHeight = (height * (0.008f + random.nextFloat() * 0.025f)).toInt().coerceAtLeast(2)
            val top = random.nextInt((height - sliceHeight).coerceAtLeast(1))
            val shift = ((random.nextFloat() - 0.5f) * width * 0.12f).toInt()
            val srcRect = Rect(0, top, width, top + sliceHeight)
            val dstRect = Rect(shift, top, width + shift, top + sliceHeight)
            canvas.drawBitmap(src, srcRect, dstRect, null)
        }
        src.recycle()
        return bitmap
    }

    private fun applyDuotone(bitmap: Bitmap, profile: CameraProfile): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val cyanotype = profile.effects.contains("CYANOTYPE_PAPER")
        val shadow = if (cyanotype) intArrayOf(12, 40, 92) else intArrayOf(14, 12, 42)
        val highlight = if (cyanotype) intArrayOf(184, 226, 240) else intArrayOf(246, 194, 82)

        for (i in pixels.indices) {
            val p = pixels[i]
            val luma = (Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f) / 255f
            pixels[i] = Color.rgb(
                (shadow[0] + (highlight[0] - shadow[0]) * luma).toInt().coerceIn(0, 255),
                (shadow[1] + (highlight[1] - shadow[1]) * luma).toInt().coerceIn(0, 255),
                (shadow[2] + (highlight[2] - shadow[2]) * luma).toInt().coerceIn(0, 255),
            )
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun applyFrameOverlay(bitmap: Bitmap, profile: CameraProfile): Bitmap {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = when {
                profile.id.contains("polaroid") -> bitmap.width * 0.085f
                profile.id.contains("super8") -> bitmap.width * 0.030f
                else -> bitmap.width * 0.024f
            }
            color = if (profile.id.contains("polaroid")) Color.rgb(246, 242, 224) else Color.BLACK
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        if (profile.id.contains("super8")) {
            val sprocket = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 20) }
            val radius = bitmap.width * 0.018f
            var y = bitmap.height * 0.12f
            while (y < bitmap.height * 0.9f) {
                canvas.drawCircle(bitmap.width * 0.055f, y, radius, sprocket)
                y += bitmap.height * 0.12f
            }
        }
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
