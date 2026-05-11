package com.vintagecam.app.ui.viewfinder

import android.Manifest
import android.content.Context
import android.opengl.GLSurfaceView
import androidx.camera.core.Preview
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.vintagecam.app.ui.theme.VintageCamTypography
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.Era
import com.vintagecam.profiles.ViewfinderType
import com.vintagecam.camera.pipeline.PreviewFilterRenderer
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ViewfinderScreen(
    viewModel: ViewfinderViewModel = hiltViewModel(),
    onOpenFilmRoll: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (cameraPermissionState.status != PermissionStatus.Granted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    when (cameraPermissionState.status) {
        is PermissionStatus.Denied -> PermissionDeniedContent()
        PermissionStatus.Granted -> {
            ViewfinderContent(
                uiState = uiState,
                onSwitchCamera = viewModel::onSwitchCamera,
                onCapture = viewModel::onCapture,
                onToggleFlash = viewModel::onToggleFlash,
                onProfilePageChanged = viewModel::onProfileSelected,
                onGalleryClick = onOpenFilmRoll,
                onOpenFilmRoll = onOpenFilmRoll,
                onStartPreview = { surfaceProvider ->
                    viewModel.onStartPreview(lifecycleOwner, surfaceProvider)
                },
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Camera permission is required",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewfinderContent(
    uiState: ViewfinderUiState,
    onSwitchCamera: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onProfilePageChanged: (Int) -> Unit,
    onGalleryClick: () -> Unit,
    onOpenFilmRoll: () -> Unit,
    onStartPreview: (Preview.SurfaceProvider) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentProfileIndex,
        pageCount = { uiState.profiles.size.coerceAtLeast(1) },
    )
    val currentProfile = uiState.profiles.getOrNull(uiState.currentProfileIndex)
    val previewShape = currentProfile?.previewShape()
    val previewModifier = Modifier.fillMaxSize().then(
        if (previewShape != null) Modifier.clip(previewShape) else Modifier,
    )

    LaunchedEffect(pagerState.currentPage) {
        if (uiState.profiles.isNotEmpty()) {
            onProfilePageChanged(pagerState.currentPage)
        }
    }

    LaunchedEffect(uiState.currentProfileIndex) {
        if (pagerState.currentPage != uiState.currentProfileIndex) {
            pagerState.animateScrollToPage(uiState.currentProfileIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = previewModifier,
            userScrollEnabled = uiState.profiles.isNotEmpty(),
        ) { _ ->
            CameraPreview(
                context = context,
                modifier = previewModifier,
                profile = currentProfile,
                onStartPreview = onStartPreview,
            )
        }

        currentProfile?.let {
            ProfileChromeOverlay(profile = it, modifier = previewModifier)
        }

        TopControlsRow(
            flashEnabled = uiState.flashEnabled,
            onToggleFlash = onToggleFlash,
            onSwitchCamera = onSwitchCamera,
            onGalleryClick = onGalleryClick,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )

        CaptureArea(
            profile = currentProfile,
            cameraState = uiState.cameraState,
            onCapture = {
                scope.launch { onCapture() }
            },
            onOpenFilmRoll = onOpenFilmRoll,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun CameraPreview(
    context: Context,
    modifier: Modifier,
    profile: CameraProfile?,
    onStartPreview: (Preview.SurfaceProvider) -> Unit,
) {
    var previewStarted by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            androidx.camera.view.PreviewView(ctx).apply {
                scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_START
            }
        },
        modifier = modifier,
        update = { previewView ->
            if (!previewStarted) {
                previewStarted = true
                try {
                    onStartPreview(previewView.surfaceProvider)
                } catch (e: Exception) {
                    android.util.Log.e("Viewfinder", "Failed to start preview", e)
                }
            }
        }
    )

    // If we still want to apply shader profile updates elsewhere, keep using the profile value
}

@Composable
private fun TopControlsRow(
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleFlash) {
            Icon(
                imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = if (flashEnabled) "Turn flash off" else "Turn flash on",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onSwitchCamera) {
                Icon(
                    imageVector = Icons.Filled.SwitchCamera,
                    contentDescription = "Switch camera",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp),
                )
            }

            IconButton(onClick = onGalleryClick) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = "Open gallery",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CaptureArea(
    profile: CameraProfile?,
    cameraState: CameraState,
    onCapture: () -> Unit,
    onOpenFilmRoll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "capture-scale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val profileFont = profile?.fontFamily() ?: VintageCamTypography.digitalFont
        Text(
            text = profile?.displayName.orEmpty().uppercase(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = profileFont,
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(Color.Black)
                .border(4.dp, Color.White, CircleShape)
                .pointerInput(onOpenFilmRoll) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -20f) {
                            onOpenFilmRoll()
                        }
                    }
                }
                .clickable(
                    enabled = cameraState == CameraState.Previewing,
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCapture()
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }

        Text(
            text = cameraStateLabel(cameraState),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontFamily = profileFont,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileChromeOverlay(
    profile: CameraProfile,
    modifier: Modifier = Modifier,
) {
    when (profile.viewfinderType) {
        ViewfinderType.CRT -> VhsOverlay(modifier = modifier)
        ViewfinderType.OPTICAL -> DisposableOverlay(modifier = modifier)
        ViewfinderType.LCD -> DigicamOverlay(modifier = modifier)
    }
}

@Composable
private fun VhsOverlay(modifier: Modifier = Modifier) {
    val blinkAlpha by rememberInfiniteTransition(label = "rec-blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-alpha",
    )
    val batteryLevel by rememberInfiniteTransition(label = "vhs-battery").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "battery-level",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 8.dp, bottomEnd = 8.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanlineSpacing = 4.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += scanlineSpacing
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.2f),
                    ),
                    center = center,
                    radius = size.minDimension * 0.82f,
                ),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(Color.Red.copy(alpha = blinkAlpha))
            }
            Text(
                text = "REC",
                color = Color.Red.copy(alpha = blinkAlpha),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = VintageCamTypography.vhsFont,
            )
        }

        BatteryMeter(
            level = batteryLevel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
    }
}

@Composable
private fun BatteryMeter(
    level: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(28.dp, 14.dp)) {
        val bodyWidth = size.width - 4f
        val bodyHeight = size.height
        drawRoundRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(0f, 0f),
            size = Size(bodyWidth, bodyHeight),
            style = Stroke(width = 1.4f),
        )
        drawRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(bodyWidth, bodyHeight * 0.3f),
            size = Size(4f, bodyHeight * 0.4f),
        )

        val fillWidth = (bodyWidth - 4f) * level.coerceIn(0f, 1f)
        drawRect(
            color = if (level > 0.4f) Color.White.copy(alpha = 0.9f) else Color(0xFFFFD700),
            topLeft = Offset(2f, 2f),
            size = Size(fillWidth, bodyHeight - 4f),
        )
    }
}

@Composable
private fun DisposableOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "1998.05.11",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color(0xFFFFD700),
            fontSize = 12.sp,
            fontFamily = VintageCamTypography.filmFont,
            fontWeight = FontWeight.Bold,
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.6f)
                .fillMaxHeight(0.45f)
                .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp)),
        )

        Text(
            text = "024",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DigicamOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = 0.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.65f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BatteryFull,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("3B", color = Color.White, fontSize = 10.sp, fontFamily = VintageCamTypography.digitalFont)
                    Icon(
                        imageVector = Icons.Filled.SdStorage,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("SD", color = Color.White, fontSize = 10.sp, fontFamily = VintageCamTypography.digitalFont)
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("AUTO", color = Color.White, fontSize = 10.sp, fontFamily = VintageCamTypography.digitalFont)
                }

                Text(
                    "2.0MP",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = VintageCamTypography.digitalFont,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter),
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)),
                ),
            )
        }
    }
}

private fun cameraStateLabel(cameraState: CameraState): String {
    return when (cameraState) {
        CameraState.Previewing -> "READY"
        CameraState.Capturing -> "REC"
        CameraState.Processing -> "BUSY"
    }
}

private fun CameraProfile.previewShape(): RoundedCornerShape? {
    return when (viewfinderType) {
        ViewfinderType.CRT -> RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 32.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp,
        )
        else -> null
    }
}

private fun CameraProfile.fontFamily(): FontFamily {
    return when (era) {
        Era.EIGHTIES -> VintageCamTypography.vhsFont
        Era.NINETIES -> VintageCamTypography.filmFont
        Era.TWO_THOUSANDS -> VintageCamTypography.digitalFont
    }
}
