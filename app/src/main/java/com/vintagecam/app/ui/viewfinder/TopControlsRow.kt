package com.vintagecam.app.ui.viewfinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun TopControlsRow(
    flashEnabled: Boolean,
    controlsEnabled: Boolean,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = Color.White.copy(alpha = if (controlsEnabled) 0.8f else 0.3f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleFlash,
            enabled = controlsEnabled,
        ) {
            Icon(
                imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = if (flashEnabled) "Turn flash off" else "Turn flash on",
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onSwitchCamera,
                enabled = controlsEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.SwitchCamera,
                    contentDescription = "Switch camera",
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }

            IconButton(
                onClick = onGalleryClick,
                enabled = controlsEnabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = "Open gallery",
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
