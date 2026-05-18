package com.vintagecam.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.vintagecam.app.BuildConfig
import com.vintagecam.profiles.data.SavedPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun sharePhoto(photo: SavedPhoto) {
        if (photo.isProcessing || photo.errorMessage != null || photo.filePath.isBlank()) return
        val bitmap = BitmapFactory.decodeFile(photo.filePath) ?: return
        val export = renderShareCard(bitmap, photo)
        bitmap.recycle()
        shareBitmap(export, "vintagecam_${photo.id}.jpg")
        export.recycle()
    }

    fun shareStrip(photos: List<SavedPhoto>) {
        val ready = photos.filter { !it.isProcessing && it.errorMessage == null && it.filePath.isNotBlank() }.take(4)
        if (ready.isEmpty()) return
        val bitmaps = ready.mapNotNull { BitmapFactory.decodeFile(it.filePath) }
        if (bitmaps.isEmpty()) return
        val export = renderPhotoStrip(bitmaps, ready)
        bitmaps.forEach { it.recycle() }
        shareBitmap(export, "vintagecam_roll_${System.currentTimeMillis()}.jpg")
        export.recycle()
    }

    private fun renderShareCard(source: Bitmap, photo: SavedPhoto): Bitmap {
        val targetWidth = 1440
        val border = 72
        val footer = 190
        val imageHeight = ((source.height / source.width.toFloat()) * (targetWidth - border * 2)).toInt()
        val targetHeight = imageHeight + border + footer
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(244, 241, 226))

        val imageRect = Rect(border, border, targetWidth - border, border + imageHeight)
        canvas.drawBitmap(source, null, imageRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        drawMetadata(canvas, photo, border.toFloat(), (border + imageHeight + 76).toFloat(), targetWidth - border * 2)
        return output
    }

    private fun renderPhotoStrip(bitmaps: List<Bitmap>, photos: List<SavedPhoto>): Bitmap {
        val targetWidth = 1080
        val gap = 24
        val margin = 54
        val frameWidth = targetWidth - margin * 2
        val frameHeight = (frameWidth * 1.24f).toInt()
        val footer = 150
        val outputHeight = margin + bitmaps.size * frameHeight + (bitmaps.size - 1) * gap + footer
        val output = Bitmap.createBitmap(targetWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(20, 20, 18))

        var top = margin
        bitmaps.forEach { bitmap ->
            val rect = Rect(margin, top, margin + frameWidth, top + frameHeight)
            canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            top += frameHeight + gap
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        canvas.drawText("SHOT ON VINTAGECAM", margin.toFloat(), (outputHeight - 84).toFloat(), paint)
        paint.textSize = 24f
        paint.typeface = Typeface.MONOSPACE
        paint.color = Color.WHITE
        paint.alpha = 150
        canvas.drawText("${photos.size} FRAMES  #vintagecam #35mm", margin.toFloat(), (outputHeight - 42).toFloat(), paint)
        return output
    }

    private fun drawMetadata(canvas: Canvas, photo: SavedPhoto, x: Float, baseline: Float, width: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 27, 18)
            textSize = 38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(88, 78, 58)
            textSize = 28f
            typeface = Typeface.MONOSPACE
        }

        canvas.drawText(photo.profileName.uppercase(Locale.US), x, baseline, titlePaint)
        val date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(Date(photo.timestampMillis))
        val caption = "$date  SHOT ON VINTAGECAM"
        canvas.drawText(caption, x, baseline + 48f, metaPaint)
        val tag = "#vintagecam #35mm #retrocam"
        canvas.drawText(tag, x + width - metaPaint.measureText(tag), baseline + 48f, metaPaint)
    }

    private fun shareBitmap(bitmap: Bitmap, fileName: String) {
        val dir = File(context.cacheDir, "share_exports").also { it.mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Shot on VintageCam #vintagecam #35mm")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share VintageCam shot").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Handler(Looper.getMainLooper()).post {
            context.startActivity(chooser)
        }
    }
}
