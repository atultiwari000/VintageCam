package com.vintagecam.camera.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.DateStampStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CapturePostProcessor @Inject constructor() {
    fun apply(input: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        applyDateStamp(canvas, output.width, output.height, profile.dateStampStyle, capturedAtMillis)
        if (profile.dateStampStyle == DateStampStyle.RED_LED) {
            applyFilmBorder(canvas, output.width, output.height)
        }
        return output
    }

    private fun applyDateStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        style: DateStampStyle,
        capturedAtMillis: Long,
    ) {
        val timestamp = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(capturedAtMillis))
        when (style) {
            DateStampStyle.NONE -> Unit
            DateStampStyle.YELLOW_CLASSIC -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.YELLOW
                    textSize = 42f
                }
                canvas.drawText(timestamp, 20f, height - 28f, paint)
            }
            DateStampStyle.WHITE_LCD -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    textSize = 28f
                }
                val bounds = RectF(width - 280f, height - 64f, width - 16f, height - 16f)
                canvas.drawText(timestamp, bounds.left + 8f, bounds.bottom - 12f, paint)
            }
            DateStampStyle.RED_LED -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    textSize = 32f
                }
                canvas.drawText(timestamp, 24f, height - 24f, paint)
            }
        }
    }

    private fun applyFilmBorder(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = AndroidColor.WHITE
            strokeWidth = 24f
        }
        canvas.drawRect(12f, 12f, width - 12f, height - 12f, paint)
    }
}
