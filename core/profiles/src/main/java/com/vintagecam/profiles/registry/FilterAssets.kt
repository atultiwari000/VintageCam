package com.vintagecam.profiles.registry

data class FilterAssets(
    val lut: String? = null,
    val overlay: String? = null,
    val grain: String? = null,
    val frame: String? = null,
    val assetStatus: AssetStatus = AssetStatus.PLACEHOLDER,
)

enum class AssetStatus {
    AVAILABLE,
    PLACEHOLDER,
    MISSING,
    DOWNLOADING,
    FAILED,
}
