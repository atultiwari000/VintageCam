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
import android.graphics.RectF
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
        if ("COOL_VINTAGE_PRINT" in effects) result = applyCoolVintagePrint(result)
        if ("FRAME_OVERLAY" in effects) result = applyFrameOverlay(result, profile)
        if ("ASCII_CHAR_PHOTO" in effects) result = applyAsciiCharPhoto(result)

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
        val glitchPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        repeat(18) { index ->
            val sliceHeight = (height * (0.006f + random.nextFloat() * 0.045f)).toInt().coerceAtLeast(2)
            val top = random.nextInt((height - sliceHeight).coerceAtLeast(1))
            val shift = ((random.nextFloat() - 0.5f) * width * 0.24f).toInt()
            val srcRect = Rect(0, top, width, top + sliceHeight)
            val dstRect = Rect(shift, top, width + shift, top + sliceHeight)
            canvas.drawBitmap(src, srcRect, dstRect, null)

            glitchPaint.color = if (index % 2 == 0) {
                Color.argb(34, 255, 32, 92)
            } else {
                Color.argb(30, 32, 255, 218)
            }
            canvas.drawRect(0f, top.toFloat(), width.toFloat(), (top + sliceHeight).toFloat(), glitchPaint)
        }

        repeat(7) {
            val y = random.nextInt(height.coerceAtLeast(1)).toFloat()
            glitchPaint.color = Color.argb(48 + random.nextInt(42), 255, 255, 255)
            glitchPaint.strokeWidth = 1f + random.nextFloat() * 3f
            canvas.drawLine(0f, y, width.toFloat(), y + random.nextInt(10), glitchPaint)
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

    private fun applyAsciiCharPhoto(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val sampleWidth = (width / 160).coerceAtLeast(5)
        val sampleHeight = (sampleWidth * 1.72f).toInt().coerceAtLeast(8)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = sampleHeight * 0.92f
            isSubpixelText = false
        }
        val ramp = charArrayOf('.', ':', '-', '+', '*', '#', '$', '%', '@')
        val threshold = 42f

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val stats = sampleCell(pixels, width, height, x, y, sampleWidth, sampleHeight)
                if (stats.luma > threshold) {
                    val normalized = ((stats.luma - threshold) / (255f - threshold)).coerceIn(0f, 1f)
                    val charIndex = (normalized * (ramp.size - 1)).toInt().coerceIn(0, ramp.lastIndex)
                    val green = (142 + normalized * 113f).toInt().coerceIn(0, 255)
                    val alpha = (145 + normalized * 110f).toInt().coerceIn(0, 255)
                    paint.color = Color.argb(alpha, 0, green, 72)
                    canvas.drawText(ramp[charIndex].toString(), x.toFloat(), (y + sampleHeight).toFloat(), paint)
                }
                x += sampleWidth
            }
            y += sampleHeight
        }

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, 0, 255, 78)
            style = Paint.Style.STROKE
            strokeWidth = (height / 420f).coerceAtLeast(1f)
        }
        var scanY = 0f
        val scanSpacing = (sampleHeight * 0.64f).coerceAtLeast(5f)
        while (scanY < height) {
            canvas.drawLine(0f, scanY, width.toFloat(), scanY, glowPaint)
            scanY += scanSpacing
        }

        if (bitmap != result && !bitmap.isRecycled) bitmap.recycle()
        return result
    }

    private fun applyCoolVintagePrint(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = pixel ushr 24
            val r0 = Color.red(pixel)
            val g0 = Color.green(pixel)
            val b0 = Color.blue(pixel)
            val luma = (r0 * 0.299f + g0 * 0.587f + b0 * 0.114f).coerceIn(0f, 255f)
            val tone = toneCurveForCoolPrint(luma / 255f)
            val chroma = 0.34f
            val warmSkinBias = if (r0 > b0 && r0 >= g0 * 0.82f && g0 > b0 * 0.70f) 1f else 0f

            val coolR = 32f + tone * 208f
            val coolG = 36f + tone * 198f
            val coolB = 48f + tone * 190f
            val preserveR = luma + (r0 - luma) * chroma
            val preserveG = luma + (g0 - luma) * chroma
            val preserveB = luma + (b0 - luma) * chroma

            var r = mix(coolR, preserveR + warmSkinBias * 10f, 0.42f)
            var g = mix(coolG, preserveG + warmSkinBias * 5f, 0.42f)
            var b = mix(coolB, preserveB - warmSkinBias * 4f, 0.42f)

            val shadowCool = (1f - tone).coerceIn(0f, 1f)
            r -= shadowCool * 7f
            g -= shadowCool * 3f
            b += shadowCool * 11f

            pixels[i] = Color.argb(
                a,
                r.toInt().coerceIn(0, 255),
                g.toInt().coerceIn(0, 255),
                b.toInt().coerceIn(0, 255),
            )
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.rgb(5, 6, 7))

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val edge = (width * 0.030f).coerceAtLeast(10f)
        val topFrame = RectF(edge, edge, width - edge, height * 0.405f)
        val lowerFrame = RectF(edge, height * 0.488f, width - edge, height - edge)
        val dividerTop = height * 0.423f
        val dividerBottom = height * 0.488f

        drawCenterCropBitmap(canvas, bitmap, topFrame, imagePaint, zoom = 1.34f, centerY = 0.44f)

        canvas.save()
        canvas.clipRect(lowerFrame)
        val cx = lowerFrame.centerX()
        val cy = lowerFrame.centerY()
        canvas.rotate(-91.5f, cx, cy)
        val rotatedTarget = RectF(
            cx - lowerFrame.height() * 0.56f,
            cy - lowerFrame.width() * 0.58f,
            cx + lowerFrame.height() * 0.56f,
            cy + lowerFrame.width() * 0.58f,
        )
        drawCenterCropBitmap(canvas, bitmap, rotatedTarget, imagePaint, zoom = 1.04f, centerY = 0.52f)
        canvas.restore()

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(4, 5, 6)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), topFrame.top, framePaint)
        canvas.drawRect(0f, topFrame.bottom, width.toFloat(), dividerTop, framePaint)
        canvas.drawRect(0f, dividerTop, width.toFloat(), dividerBottom, framePaint)
        canvas.drawRect(0f, lowerFrame.bottom, width.toFloat(), height.toFloat(), framePaint)
        canvas.drawRect(0f, 0f, edge, height.toFloat(), framePaint)
        canvas.drawRect(width - edge, 0f, width.toFloat(), height.toFloat(), framePaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 222, 152, 42)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = ((dividerBottom - dividerTop) * 0.43f).coerceAtLeast(16f)
        }
        canvas.drawText("UNFOLD 40 C-3", width * 0.15f, dividerTop + (dividerBottom - dividerTop) * 0.63f, labelPaint)

        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 216, 143, 34)
            style = Paint.Style.FILL
        }
        val arrowY = dividerTop + (dividerBottom - dividerTop) * 0.52f
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(width * 0.055f, arrowY - height * 0.010f)
            lineTo(width * 0.085f, arrowY)
            lineTo(width * 0.055f, arrowY + height * 0.010f)
            close()
        }, markerPaint)
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(width * 0.855f, arrowY - height * 0.010f)
            lineTo(width * 0.885f, arrowY)
            lineTo(width * 0.855f, arrowY + height * 0.010f)
            close()
        }, markerPaint)

        applyContactSheetTexture(result)
        if (!bitmap.isRecycled) bitmap.recycle()
        return result
    }

    private fun drawCenterCropBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        dst: RectF,
        paint: Paint,
        zoom: Float,
        centerY: Float,
    ) {
        val dstRatio = dst.width() / dst.height()
        var cropWidth = bitmap.width.toFloat()
        var cropHeight = cropWidth / dstRatio
        if (cropHeight > bitmap.height) {
            cropHeight = bitmap.height.toFloat()
            cropWidth = cropHeight * dstRatio
        }
        cropWidth /= zoom.coerceAtLeast(1f)
        cropHeight /= zoom.coerceAtLeast(1f)

        val left = ((bitmap.width - cropWidth) * 0.50f).coerceIn(0f, bitmap.width - cropWidth)
        val top = ((bitmap.height - cropHeight) * centerY).coerceIn(0f, bitmap.height - cropHeight)
        val src = Rect(
            left.toInt(),
            top.toInt(),
            (left + cropWidth).toInt().coerceAtMost(bitmap.width),
            (top + cropHeight).toInt().coerceAtMost(bitmap.height),
        )
        canvas.drawBitmap(bitmap, src, dst, paint)
    }

    private fun applyContactSheetTexture(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val random = java.util.Random(width * 73856093L xor height * 19349663L)
        val texture = Paint(Paint.ANTI_ALIAS_FLAG)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val noise = random.nextInt(37) - 18
            val speckle = if (random.nextFloat() < 0.004f) random.nextInt(70) - 45 else 0
            pixels[i] = Color.rgb(
                (Color.red(p) + noise + speckle).coerceIn(0, 255),
                (Color.green(p) + noise + speckle).coerceIn(0, 255),
                (Color.blue(p) + noise + speckle).coerceIn(0, 255),
            )
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val weaveSpacing = (width / 410f).coerceAtLeast(2f)
        texture.strokeWidth = 1f
        var x = 0f
        var column = 0
        while (x < width) {
            texture.color = if (column % 2 == 0) Color.argb(20, 255, 255, 255) else Color.argb(17, 25, 20, 22)
            canvas.drawLine(x, 0f, x, height.toFloat(), texture)
            x += weaveSpacing
            column++
        }
        var y = 0f
        var row = 0
        while (y < height) {
            texture.color = if (row % 2 == 0) Color.argb(14, 255, 255, 255) else Color.argb(14, 16, 13, 15)
            canvas.drawLine(0f, y, width.toFloat(), y, texture)
            y += weaveSpacing * 1.28f
            row++
        }

        texture.style = Paint.Style.STROKE
        texture.strokeWidth = (width / 680f).coerceAtLeast(1f)
        repeat(18) {
            val x0 = random.nextFloat() * width
            val y0 = random.nextFloat() * height
            texture.color = if (it % 3 == 0) Color.argb(58, 10, 8, 7) else Color.argb(42, 255, 250, 235)
            canvas.drawLine(
                x0,
                y0,
                x0 + (random.nextFloat() - 0.5f) * width * 0.08f,
                y0 + height * (0.04f + random.nextFloat() * 0.14f),
                texture,
            )
        }
    }

    private fun sampleCell(
        pixels: IntArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        sampleWidth: Int,
        sampleHeight: Int,
    ): CellStats {
        var lumaSum = 0f
        var count = 0
        val right = (left + sampleWidth).coerceAtMost(width)
        val bottom = (top + sampleHeight).coerceAtMost(height)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val p = pixels[y * width + x]
                lumaSum += Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f
                count++
                x += 2
            }
            y += 2
        }
        return CellStats(if (count == 0) 0f else lumaSum / count)
    }

    private fun toneCurveForCoolPrint(value: Float): Float {
        val lifted = value * 0.88f + 0.07f
        return (lifted * lifted * (3f - 2f * lifted)).coerceIn(0f, 1f)
    }

    private fun mix(a: Float, b: Float, amount: Float): Float = a + (b - a) * amount

    private data class CellStats(val luma: Float)

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
