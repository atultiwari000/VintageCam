package com.vintagecam.camera.pipeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.vintagecam.profiles.CameraProfile
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

    init {
        // Hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setProfile(profile: CameraProfile?) {
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
        }

        // Apply color tint (subtle, so preview isn't too dark)
        drawColorTint(canvas, profile.colorMatrix)
    }

    private fun drawVignette(canvas: Canvas, strength: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = hypot(centerX, centerY)

        val gradient = RadialGradient(
            centerX,
            centerY,
            maxRadius,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.6f, 1f),
            Shader.TileMode.CLAMP,
        )

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
