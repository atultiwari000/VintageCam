package com.vintagecam.profiles.registry

data class CaptureHints(
    val latencyMs: Long = 80L,
    val aspectRatio: String = "RATIO_4_3",
    val viewfinderType: String = "OPTICAL",
    val dateStampStyle: String = "NONE",
    val flashBehavior: String = "OFF",
    val deviceLabel: String = "",
    val soundId: String = "default",
)
