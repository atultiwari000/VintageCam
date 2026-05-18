package com.vintagecam.app.ui.viewfinder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vintagecam.app.ui.theme.VintageCamTypography
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.ComputationalMode
import com.vintagecam.profiles.Era
import kotlinx.coroutines.delay

@Composable
internal fun CaptureArea(
    profile: CameraProfile?,
    cameraState: CameraState,
    developingCount: Int,
    onCapture: () -> Unit,
    onOpenFilmRoll: () -> Unit,
    showProfileLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val profileFont = profileFont(profile)
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(cameraState) {
        if (cameraState == CameraState.Capturing) {
            showFlash = true
            delay(100)
            showFlash = false
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProfileLabel) {
            Text(
                text = profile?.displayName.orEmpty().uppercase(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = profileFont,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Text(
            text = when (cameraState) {
                CameraState.Capturing -> if (profile.usesComputationalCapture()) "MERGE ${profile?.burstFrameCount ?: 1}F" else "BUSY"
                CameraState.Processing -> "PROC"
                CameraState.Previewing -> when {
                    developingCount > 0 -> "DEV $developingCount"
                    profile.usesComputationalCapture() -> "HQ ${profile?.burstFrameCount ?: 1}F"
                    else -> "READY"
                }
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = profileFont,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 7.dp),
        )

        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            CaptureButton(
                onClick = onCapture,
                onSwipeUp = onOpenFilmRoll,
                enabled = cameraState == CameraState.Previewing,
                developingCount = developingCount,
            )

            if (showFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun CaptureButton(
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    enabled: Boolean,
    developingCount: Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "capture-scale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    },
                )
            }
            .pointerInput(enabled, onSwipeUp) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (enabled && dragAmount < -20f) {
                        onSwipeUp()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                color = Color.Black.copy(alpha = 0.42f),
                radius = radius,
            )
            drawCircle(
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f),
                radius = radius - 2.dp.toPx(),
                style = Stroke(width = 4.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = radius - 10.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = 2.dp.toPx(),
                center = Offset(size.width * 0.34f, size.height * 0.30f),
            )
        }

        if (developingCount > 0) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(0.96f),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.18f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize(if (isPressed) 0.64f else 0.70f)
                .background(
                    if (enabled) Color.Black.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.42f),
                    CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
        )
    }
}

private fun profileFont(profile: CameraProfile?): FontFamily {
    return when (profile?.era) {
        Era.EIGHTIES -> VintageCamTypography.vhsFont
        Era.NINETIES -> VintageCamTypography.filmFont
        Era.TWO_THOUSANDS -> VintageCamTypography.digitalFont
        null -> VintageCamTypography.digitalFont
    }
}

private fun CameraProfile?.usesComputationalCapture(): Boolean {
    return this != null && computationalMode != ComputationalMode.SINGLE && burstFrameCount > 1
}
