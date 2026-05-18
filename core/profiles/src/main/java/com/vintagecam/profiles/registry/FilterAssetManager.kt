package com.vintagecam.profiles.registry

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FilterAssetManager {
    suspend fun ensureLutAvailable(path: String): File?
    suspend fun ensureOverlayAvailable(path: String): File?
    suspend fun ensureGrainAvailable(path: String): File?
    fun assetStatus(path: String?): AssetStatus
}

@Singleton
class AndroidFilterAssetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : FilterAssetManager {

    override suspend fun ensureLutAvailable(path: String): File? = copyAssetIfAvailable(path)

    override suspend fun ensureOverlayAvailable(path: String): File? = copyAssetIfAvailable(path)

    override suspend fun ensureGrainAvailable(path: String): File? = copyAssetIfAvailable(path)

    override fun assetStatus(path: String?): AssetStatus {
        if (path.isNullOrBlank()) return AssetStatus.AVAILABLE
        return if (assetExists(path)) AssetStatus.AVAILABLE else AssetStatus.MISSING
    }

    private suspend fun copyAssetIfAvailable(path: String): File? = withContext(Dispatchers.IO) {
        if (!assetExists(path)) {
            Log.w("FilterAssetManager", "Missing filter asset: $path")
            return@withContext null
        }

        val outFile = File(context.cacheDir, "filter_assets/$path")
        if (outFile.exists()) return@withContext outFile
        outFile.parentFile?.mkdirs()
        context.assets.open(path).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        outFile
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
