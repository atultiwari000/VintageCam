package com.vintagecam.camera.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import com.vintagecam.profiles.CameraProfile
import javax.inject.Inject

interface ShaderPipeline {
    fun apply(input: Bitmap, profile: CameraProfile): Bitmap
}

class CpuFilterApplier @Inject constructor() : ShaderPipeline {

    override fun apply(input: Bitmap, profile: CameraProfile): Bitmap {
        return applyProfile(input, profile)
    }

    fun applyProfile(input: Bitmap, profile: CameraProfile): Bitmap {
        val mutableBitmap = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(profile.colorMatrix))
        }

        val colorAdjusted = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val colorCanvas = Canvas(colorAdjusted)
        colorCanvas.drawBitmap(input, 0f, 0f, colorPaint)

        canvas.drawBitmap(colorAdjusted, 0f, 0f, null)
        applyRadialVignette(canvas, mutableBitmap.width, mutableBitmap.height, profile.vignetteStrength)

        return mutableBitmap
    }

    private fun applyRadialVignette(canvas: Canvas, width: Int, height: Int, strength: Float) {
        val clampedStrength = strength.coerceIn(0f, 1f)
        if (clampedStrength <= 0f) return

        val radius = maxOf(width, height) * 0.75f
        val alpha = (180f * clampedStrength).toInt().coerceIn(0, 255)

        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f,
                height / 2f,
                radius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(alpha, 0, 0, 0),
                ),
                floatArrayOf(0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        canvas.drawRect(Rect(0, 0, width, height), vignettePaint)
    }
}
