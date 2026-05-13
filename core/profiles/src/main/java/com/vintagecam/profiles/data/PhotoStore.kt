package com.vintagecam.profiles.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent photo store backed by internal storage.
 *
 * Photos are always saved to app-private storage (filesDir/photos/).
 * A best-effort MediaStore copy is made for system gallery visibility.
 *
 * Thread safety: All public methods are thread-safe. Call from any dispatcher.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photosDir: File
        get() = File(context.filesDir, "photos").also { it.mkdirs() }

    // ── Save ───────────────────────────────────────────────────────────

    /**
     * Save a processed bitmap to internal storage and best-effort to MediaStore.
     *
     * @return SavedPhoto with the internal file path. URI is the internal file URI.
     */
    fun save(bitmap: Bitmap, profileId: String, profileName: String, timestamp: Long): SavedPhoto {
        // 1. Internal storage (always succeeds, no permissions needed)
        val internalFile = saveToInternal(bitmap, profileId, timestamp)

        // 2. Best-effort MediaStore (system gallery)
        try {
            saveToMediaStore(internalFile, profileName, timestamp)
        } catch (e: Exception) {
            Log.w("PhotoStore", "MediaStore save failed (non-fatal)", e)
        }

        return SavedPhoto(
            id = "${profileId}_$timestamp",
            filePath = internalFile.absolutePath,
            profileId = profileId,
            profileName = profileName,
            timestampMillis = timestamp,
        )
    }

    private fun saveToInternal(bitmap: Bitmap, profileId: String, timestamp: Long): File {
        val file = File(photosDir, "${profileId}_$timestamp.jpg")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                throw java.io.IOException("Bitmap.compress returned false")
            }
        }
        Log.d("PhotoStore", "Saved to internal: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    private fun saveToMediaStore(file: File, profileName: String, timestamp: Long) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VintageCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri: Uri = resolver.insert(collection, values)
            ?: throw java.io.IOException("MediaStore insert returned null")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw java.io.IOException("Failed to open MediaStore output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val update = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, update, null, null)
            }
            Log.d("PhotoStore", "Saved to MediaStore: $uri")
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            Log.w("PhotoStore", "MediaStore write failed, cleaned up", e)
        }
    }

    // ── Load ───────────────────────────────────────────────────────────

    /**
     * Load all saved photos, ordered newest first.
     */
    fun loadAll(): List<SavedPhoto> {
        val files = photosDir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }
            ?: emptyArray()

        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file -> parsePhotoFile(file) }
    }

    /**
     * Load a single photo by ID.
     */
    fun loadById(id: String): SavedPhoto? {
        val file = File(photosDir, "$id.jpg")
        return if (file.exists()) parsePhotoFile(file) else null
    }

    /**
     * Decode a thumbnail-sized bitmap for gallery display.
     */
    fun loadThumbnail(id: String, maxWidth: Int = 480): Bitmap? {
        val file = File(photosDir, "$id.jpg")
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        options.inSampleSize = calculateInSampleSize(
            options.outWidth, options.outHeight, maxWidth, maxWidth
        )
        options.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Decode the full-resolution bitmap for the full-screen viewer.
     */
    fun loadFull(id: String): Bitmap? {
        val file = File(photosDir, "$id.jpg")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    // ── Delete ─────────────────────────────────────────────────────────

    fun delete(id: String): Boolean {
        val file = File(photosDir, "$id.jpg")
        return if (file.exists()) {
            val deleted = file.delete()
            Log.d("PhotoStore", "Deleted: $id → success=$deleted")
            deleted
        } else {
            false
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────

    private fun parsePhotoFile(file: File): SavedPhoto? {
        // Filename format: {profileId}_{timestamp}.jpg
        val nameNoExt = file.nameWithoutExtension
        val lastUnderscore = nameNoExt.lastIndexOf('_')
        if (lastUnderscore < 0) return null

        val profileId = nameNoExt.substring(0, lastUnderscore)
        val timestamp = nameNoExt.substring(lastUnderscore + 1).toLongOrNull() ?: return null

        return SavedPhoto(
            id = nameNoExt,
            filePath = file.absolutePath,
            profileId = profileId,
            profileName = profileIdToName(profileId),
            timestampMillis = timestamp,
        )
    }

    private fun profileIdToName(id: String): String = when (id) {
        "vhs_1985" -> "VHS-C 1985"
        "disposable_1998" -> "FunSaver '98"
        "digicam_2003" -> "CyberShot 2003"
        else -> id
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int,
        reqWidth: Int, reqHeight: Int,
    ): Int {
        var sampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfH = rawHeight / 2
            val halfW = rawWidth / 2
            while (halfH / sampleSize >= reqHeight && halfW / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}
