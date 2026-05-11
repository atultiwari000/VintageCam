package com.vintagecam.camera.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.vintagecam.profiles.CameraProfile
import java.io.IOException

internal class GallerySaver(
    private val context: Context,
) {
    fun save(bitmap: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Uri {
        val resolver = context.contentResolver
        val fileName = "VintageCam_${profile.id}_$capturedAtMillis.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, capturedAtMillis)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/VintageCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to insert image into MediaStore")

        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw IOException("Failed to write JPEG to MediaStore")
            }
        } ?: throw IOException("Unable to open output stream for MediaStore URI")

        resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifTimestamp(capturedAtMillis))
                setAttribute(ExifInterface.TAG_DATETIME, exifTimestamp(capturedAtMillis))
                setAttribute(ExifInterface.TAG_MAKE, "VintageCam")
                setAttribute(ExifInterface.TAG_MODEL, profile.displayName)
                setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, profile.id)
                saveAttributes()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return uri
    }

    private fun exifTimestamp(capturedAtMillis: Long): String {
        return java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(capturedAtMillis))
    }
}
