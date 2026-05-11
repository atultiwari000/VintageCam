package com.vintagecam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VintageDarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF000000),
    secondary = androidx.compose.ui.graphics.Color(0xFF9A9A9A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surface = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
)

@Composable
fun VintageCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VintageDarkColorScheme, content = content)
}
