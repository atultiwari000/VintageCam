package com.vintagecam.camera.pipeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.DateStampStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

/**
 * Transparent overlay view that draws profile-specific effects on top of the camera preview.
 *
 * Effects are applied in real-time and are purely visual (non-destructive).
 * The full-strength effects are applied during capture by CapturePostProcessor.
 *
 * Performance: Must complete onDraw in < 16ms (60fps target).
 * - No bitmap allocations
 * - Reuse Paint objects
 * - Use hardware acceleration
 */
class PreviewOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var currentProfile: CameraProfile? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }
    private val timeFormat = SimpleDateFormat("yyyy.MM.dd", Locale.US)
    private var vignetteShader: RadialGradient? = null
    private var vignetteWidth = 0
    private var vignetteHeight = 0
    private var lightLeakShader: RadialGradient? = null
    private var lightLeakWidth = 0
    private var lightLeakHeight = 0

    init {
        // Hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setProfile(profile: CameraProfile?) {
        if (currentProfile?.id == profile?.id) return
        currentProfile = profile
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val profile = currentProfile ?: return

        // Apply vignette overlay (subtle for preview)
        if (profile.vignetteStrength > 0) {
            drawVignette(canvas, profile.vignetteStrength)
        }

        // Apply scanlines if this profile has them
        if (profile.interlacedPreview) {
            drawScanlines(canvas)
            drawDropout(canvas)
        }

        // Apply color tint (subtle, so preview isn't too dark)
        drawColorTint(canvas, profile.colorMatrix)

        val effects = profile.effects.toSet()
        if ("LIGHT_LEAK" in effects) drawLightLeak(canvas)
        if ("JPEG_BLOCKS" in effects) drawJpegBlocks(canvas)
        if ("GLITCH_SLICES" in effects) drawGlitchSlices(canvas)
        if ("FRAME_OVERLAY" in effects) drawFrameHint(canvas, profile)
        if (profile.dateStampStyle != DateStampStyle.NONE || "DATE_STAMP" in effects) {
            drawDateStamp(canvas, profile.dateStampStyle)
        }

        if (profile.interlacedPreview || "VHS_DROPOUT" in effects || "GLITCH_SLICES" in effects) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawVignette(canvas: Canvas, strength: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = hypot(centerX, centerY)

        val gradient = if (vignetteShader == null || vignetteWidth != width || vignetteHeight != height) {
            vignetteWidth = width
            vignetteHeight = height
            RadialGradient(
                centerX,
                centerY,
                maxRadius,
                intArrayOf(Color.TRANSPARENT, Color.BLACK),
                floatArrayOf(0.6f, 1f),
                Shader.TileMode.CLAMP,
            ).also { vignetteShader = it }
        } else {
            vignetteShader
        }

        paint.shader = gradient
        paint.alpha = (strength * 120).toInt() // 0–120 alpha (subtle for preview)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.alpha = 255
    }

    private fun drawScanlines(canvas: Canvas) {
        paint.color = Color.BLACK
        paint.strokeWidth = 1f
        paint.alpha = 25 // Very subtle for preview (minimal visual noise)

        val spacing = 4f
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += spacing
        }

        paint.alpha = 255
    }

    private fun drawDropout(canvas: Canvas) {
        val phase = (System.currentTimeMillis() / 1400L) % 7L
        if (phase != 0L) return

        paint.color = Color.WHITE
        paint.alpha = 32
        paint.strokeWidth = 2f
        val y = ((System.currentTimeMillis() / 19L) % height.coerceAtLeast(1)).toFloat()
        canvas.drawLine(0f, y, width.toFloat(), y + 8f, paint)
        paint.alpha = 255
    }

    private fun drawLightLeak(canvas: Canvas) {
        val gradient = if (lightLeakShader == null || lightLeakWidth != width || lightLeakHeight != height) {
            lightLeakWidth = width
            lightLeakHeight = height
            RadialGradient(
                width * 0.96f,
                height * 0.08f,
                width * 0.46f,
                intArrayOf(Color.argb(72, 255, 92, 30), Color.argb(30, 255, 215, 70), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            ).also { lightLeakShader = it }
        } else {
            lightLeakShader
        }

        paint.shader = gradient
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawGlitchSlices(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val pulse = ((now / 90L) % 9L).toInt()
        val bandHeight = (height * 0.012f).coerceAtLeast(5f)
        val drift = ((now / 17L) % width.coerceAtLeast(1)).toFloat()

        paint.shader = null
        paint.strokeWidth = 2f
        paint.style = Paint.Style.FILL

        repeat(5) { i ->
            val y = ((now / (23L + i * 11L) + i * height / 5L) % height.coerceAtLeast(1)).toFloat()
            val offset = ((pulse - 4) * (i + 1) * width * 0.006f)

            paint.color = if (i % 2 == 0) Color.rgb(255, 45, 90) else Color.rgb(45, 255, 220)
            paint.alpha = 34 + i * 5
            canvas.drawRect(
                offset,
                y,
                width.toFloat() + offset,
                (y + bandHeight * (1.4f + i * 0.22f)).coerceAtMost(height.toFloat()),
                paint,
            )

            paint.color = Color.WHITE
            paint.alpha = 24
            canvas.drawLine(0f, y + bandHeight, width.toFloat(), y + bandHeight + ((i % 2) * 4f), paint)
        }

        paint.color = Color.BLACK
        paint.alpha = 38
        val tearY = ((now / 31L) % height.coerceAtLeast(1)).toFloat()
        canvas.drawRect(drift - width * 0.18f, tearY, drift + width * 0.34f, tearY + bandHeight, paint)
        paint.alpha = 255
    }

    private fun drawJpegBlocks(canvas: Canvas) {
        val block = (width / 34f).coerceAtLeast(12f)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.alpha = 16
        var y = 0f
        var row = 0
        while (y < height) {
            var x = 0f
            var col = 0
            while (x < width) {
                if ((row + col) % 4 == 0) {
                    canvas.drawRect(x, y, (x + block).coerceAtMost(width.toFloat()), (y + block).coerceAtMost(height.toFloat()), paint)
                }
                x += block
                col++
            }
            y += block
            row++
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawFrameHint(canvas: Canvas, profile: CameraProfile) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (profile.id.contains("polaroid")) width * 0.08f else width * 0.025f
        paint.color = if (profile.id.contains("polaroid")) Color.rgb(246, 242, 224) else Color.BLACK
        paint.alpha = if (profile.id.contains("polaroid")) 170 else 150
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawDateStamp(canvas: Canvas, style: DateStampStyle) {
        paint.shader = null
        paint.color = when (style) {
            DateStampStyle.RED_LED -> Color.rgb(255, 58, 44)
            DateStampStyle.WHITE_LCD -> Color.WHITE
            else -> Color.rgb(255, 214, 40)
        }
        paint.alpha = 230
        paint.textSize = (height * 0.026f).coerceAtLeast(18f)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.setShadowLayer(3f, 1f, 1f, Color.BLACK)
        canvas.drawText(timeFormat.format(Date()), width * 0.055f, height * 0.93f, paint)
        paint.clearShadowLayer()
        paint.typeface = Typeface.DEFAULT
        paint.alpha = 255
    }

    private fun drawColorTint(canvas: Canvas, matrix: FloatArray) {
        // Profile stores a full 20-element 4x5 ColorMatrix directly.
        // Skip if it's a zero/identity matrix (all zeros except diagonal = 1).
        if (matrix.size < 20) return

        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        paint.alpha = 100 // Subtle overlay (100/255 ≈ 39% opacity)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.colorFilter = null
        paint.alpha = 255
    }
}
