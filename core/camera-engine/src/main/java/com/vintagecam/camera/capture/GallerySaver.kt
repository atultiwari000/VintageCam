package com.vintagecam.camera.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.vintagecam.profiles.CameraProfile
import java.io.IOException

/**
 * Saves a processed bitmap to the device gallery via MediaStore.
 *
 * Thread safety: NOT thread-safe — caller must serialize access
 * (e.g. via [kotlinx.coroutines.Dispatchers.IO]).
 *
 * API level coverage:
 *   API 26-28  → EXTERNAL_CONTENT_URI (requires WRITE_EXTERNAL_STORAGE runtime permission)
 *   API 29 (Q) → EXTERNAL_CONTENT_URI + RELATIVE_PATH + IS_PENDING
 *   API 30+    → VOLUME_EXTERNAL_PRIMARY + RELATIVE_PATH + IS_PENDING
 */
internal class GallerySaver(
    private val context: Context,
) {
    /**
     * Save [bitmap] to the device's Pictures/VintageCam/ directory.
     *
     * @return Content URI pointing to the saved image.
     * @throws IOException if the MediaStore insert or write fails.
     * @throws SecurityException on API 26-28 if WRITE_EXTERNAL_STORAGE is not granted.
     */
    @WorkerThread
    fun save(bitmap: Bitmap, profile: CameraProfile, capturedAtMillis: Long): Uri {
        val resolver = context.contentResolver
        val fileName = "VintageCam_${profile.id}_$capturedAtMillis.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, capturedAtMillis / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, capturedAtMillis)

            // RELATIVE_PATH is available from API 29 (Q) onward
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/VintageCam",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        // VOLUME_EXTERNAL_PRIMARY was added in API 30 (R).
        // On API 29 (Q) we still use the legacy EXTERNAL_CONTENT_URI
        // (which works with RELATIVE_PATH on Q).
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri: Uri = resolver.insert(collection, values)
            ?: throw IOException(
                "MediaStore insert returned null. " +
                    "collection=$collection fileName=$fileName",
            )

        try {
            // Write JPEG bytes
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw IOException("Bitmap.compress returned false — failed to write JPEG")
                }
            } ?: throw IOException("Unable to open output stream for $uri")

            // Write EXIF metadata best-effort. Some devices refuse rw descriptors for
            // MediaStore rows even after the JPEG is written, and that should not
            // delete the photo.
            try {
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
            } catch (e: Throwable) {
                Log.w("GallerySaver", "EXIF metadata write failed for $uri", e)
            }

            // Clear IS_PENDING flag (API 29+) so the system gallery picks it up
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, updateValues, null, null)
                } catch (e: Throwable) {
                    Log.w("GallerySaver", "Failed to clear IS_PENDING for $uri", e)
                }
            }
        } catch (e: Throwable) {
            // Best-effort cleanup — if the URI was never inserted, this may log a warning
            try {
                resolver.delete(uri, null, null)
            } catch (_: Throwable) {
                // Ignore cleanup failures
            }
            Log.e("GallerySaver", "Failed to save image to MediaStore", e)
            throw e
        }

        Log.d("GallerySaver", "Saved to: $uri")
        return uri
    }

    private fun exifTimestamp(millis: Long): String {
        return java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(millis))
    }
}
