package com.vintagecam.profiles.data

/**
 * A photo saved to persistent storage.
 *
 * @param id             Unique identifier (profileId_timestamp)
 * @param filePath       Absolute path to JPEG file in internal storage
 * @param profileId      Camera profile ID (e.g. "vhs_1985")
 * @param profileName    Human-readable profile name
 * @param timestampMillis Unix epoch millis when the photo was captured
 * @param isProcessing   True while the captured frame is being developed
 * @param errorMessage   Non-null if processing failed
 */
data class SavedPhoto(
    val id: String,
    val filePath: String,
    val profileId: String,
    val profileName: String,
    val timestampMillis: Long,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)
