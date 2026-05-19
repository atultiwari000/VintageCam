package com.vintagecam.profiles

enum class CaptureTier {
    LOW,
    COMPUTATIONAL,
}

data class CaptureTierInfo(
    val tier: CaptureTier,
    val frameCount: Int,
) {
    val isComputational: Boolean get() = tier == CaptureTier.COMPUTATIONAL
}

fun CameraProfile.captureTierInfo(): CaptureTierInfo {
    val isComputational = computationalMode != ComputationalMode.SINGLE && burstFrameCount > 1
    return CaptureTierInfo(
        tier = if (isComputational) CaptureTier.COMPUTATIONAL else CaptureTier.LOW,
        frameCount = burstFrameCount.coerceAtLeast(1),
    )
}

fun CameraProfile.usesComputationalCapture(): Boolean = captureTierInfo().isComputational