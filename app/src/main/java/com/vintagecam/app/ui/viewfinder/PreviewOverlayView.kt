package com.vintagecam.app.ui.viewfinder

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
import com.vintagecam.profiles.ShaderType
import kotlin.math.hypot

/**
 * Transparent overlay that draws profile-specific effects LIVE on top of the
 * CameraX PreviewView — vignette, scanlines, and color tint — so the user sees
 * exactly what the captured photo will look like before pressing the shutter.
 *
 * Effects are intentionally kept subtle in the preview to avoid obscuring the
 * scene. The full-strength versions are applied during capture post-processing.
 */
class PreviewOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentProfile: CameraProfile? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setProfile(profile: CameraProfile) {
        currentProfile = profile
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val profile = currentProfile ?: return

        // Apply vignette overlay
        if (profile.vignetteStrength > 0) {
            drawVignette(canvas, profile.vignetteStrength)
        }

        // Apply scanlines
        if (profile.interlacedPreview || profile.shaderPipeline.contains(ShaderType.SCANLINES)) {
            drawScanlines(canvas)
        }

        // Apply color tint (subtle, so preview isn't too dark)
        if (profile.colorMatrix.any { it != 0f }) {
            drawColorTint(canvas, profile.colorMatrix)
        }
    }

    private fun drawVignette(canvas: Canvas, strength: Float) {
        val clampedStrength = strength.coerceIn(0f, 1f)
        val radius = hypot(width / 2f, height / 2f)
        val alpha = (clampedStrength * 180).toInt().coerceIn(0, 255)

        paint.reset()
        paint.shader = RadialGradient(
            width / 2f, height / 2f,
            radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0.45f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = alpha.coerceAtMost(180)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawScanlines(canvas: Canvas) {
        paint.reset()
        paint.color = Color.BLACK
        paint.alpha = 30 // Very subtle for preview
        paint.strokeWidth = 1f
        val spacing = 4f
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += spacing
        }
    }

    private fun drawColorTint(canvas: Canvas, matrix: FloatArray) {
        val androidMatrix = toAndroidColorMatrix(matrix)
        paint.reset()
        paint.colorFilter = ColorMatrixColorFilter(androidMatrix)
        paint.alpha = 80 // Subtle for preview
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.colorFilter = null
    }

    /**
     * Converts the profile's 3x3 color matrix (9 floats) into a 4x5 Android
     * ColorMatrix (20 floats) suitable for [ColorMatrixColorFilter].
     */
    private fun toAndroidColorMatrix(matrix3x3: FloatArray): ColorMatrix {
        val m = if (matrix3x3.size >= 9) matrix3x3
        else floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        return ColorMatrix(
            floatArrayOf(
                m[0], m[1], m[2], 0f, 0f,
                m[3], m[4], m[5], 0f, 0f,
                m[6], m[7], m[8], 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
