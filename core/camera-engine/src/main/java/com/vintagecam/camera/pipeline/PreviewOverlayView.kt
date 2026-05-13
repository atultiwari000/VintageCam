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
        // The profile's colorMatrix is a 9-element 3x3 matrix
        // Skip if it's a zero matrix
        if (matrix.size < 9 || matrix.all { it == 0f }) {
            return
        }

        // Build a 20-element ColorMatrix from the 9-element 3x3 matrix
        // ColorMatrix layout: [a, b, c, d, e, f, g, h, i, j, ...]
        // where a-i are the 3x3 color component matrix
        val colorMatrix = FloatArray(20).apply {
            // Identity matrix as base
            this[0] = 1f   // R output from R
            this[6] = 1f   // G output from G
            this[12] = 1f  // B output from B
            this[18] = 1f  // A output from A
            
            // Apply 60% reduced intensity for preview subtlety
            val scale = 0.4f
            
            // Apply the 3x3 color matrix (9 elements) with reduced intensity
            // matrix layout: [r*r, r*g, r*b, g*r, g*g, g*b, b*r, b*g, b*b]
            this[0] = 0.6f + scale * matrix[0]   // Red from Red
            this[1] = scale * matrix[1]           // Red from Green  
            this[2] = scale * matrix[2]           // Red from Blue
            this[5] = scale * matrix[3]           // Green from Red
            this[6] = 0.6f + scale * matrix[4]   // Green from Green
            this[7] = scale * matrix[5]           // Green from Blue
            this[10] = scale * matrix[6]          // Blue from Red
            this[11] = scale * matrix[7]          // Blue from Green
            this[12] = 0.6f + scale * matrix[8]  // Blue from Blue
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        paint.alpha = 100 // Subtle overlay (100/255 ≈ 39% opacity)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.colorFilter = null
        paint.alpha = 255
    }
}
