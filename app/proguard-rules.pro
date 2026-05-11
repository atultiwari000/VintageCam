# Hilt Dependency Injection
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.vintagecam.** { *; }
-keepclasseswithmembernames class com.vintagecam.** {
    @dagger.hilt.* <methods>;
}

# Parcelize (Kotlin)
-keep class kotlinx.parcelize.** { *; }
-keep class com.vintagecam.profiles.** { *; }

# CameraX and Camera2
-keep class androidx.camera.** { *; }
-keepclasseswithmembernames class androidx.camera.** {
    <methods>;
    <fields>;
}

# Android Graphics
-keep class android.graphics.** { *; }
-keep class android.graphics.ColorMatrixColorFilter { *; }
-keep class android.graphics.RadialGradient { *; }

# Compose and Material3
-keep class androidx.compose.** { *; }
-keep class androidx.material3.** { *; }

# Lifecycle and ViewModel
-keep class androidx.lifecycle.** { *; }
-keepclasseswithmembernames class androidx.lifecycle.** {
    @androidx.lifecycle.* <methods>;
}

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }

# Accompanist
-keep class com.google.accompanist.** { *; }

# Navigation
-keep class androidx.navigation.** { *; }
