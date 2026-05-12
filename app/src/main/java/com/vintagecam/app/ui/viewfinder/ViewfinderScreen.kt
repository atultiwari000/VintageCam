package com.vintagecam.app.ui.viewfinder

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ViewfinderScreen(
    viewModel: ViewfinderViewModel = hiltViewModel(),
    onOpenFilmRoll: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (cameraPermissionState.status != PermissionStatus.Granted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Create the PreviewView once and keep it across recompositions.
    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_START
        }
    }

    // CRITICAL: Bind preview while this screen is visible.
    // When navigating to film roll and back, NavHost removes and
    // re-adds this composable — DisposableEffect ensures CameraX
    // rebinds every time, preventing the black screen bug.
    DisposableEffect(lifecycleOwner) {
        viewModel.bindCamera(lifecycleOwner, previewView)
        onDispose {
            viewModel.onStopPreview()
        }
    }

    when (cameraPermissionState.status) {
        is PermissionStatus.Denied -> PermissionDeniedContent()
        PermissionStatus.Granted -> {
            ViewfinderContent(
                uiState = uiState,
                previewView = previewView,
                onSwitchCamera = viewModel::onSwitchCamera,
                onCapture = viewModel::onCapture,
                onToggleFlash = viewModel::onToggleFlash,
                onProfilePageChanged = viewModel::onProfileSelected,
                onGalleryClick = onOpenFilmRoll,
                onOpenFilmRoll = onOpenFilmRoll,
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
    previewView: androidx.camera.view.PreviewView,
    onSwitchCamera: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onProfilePageChanged: (Int) -> Unit,
    onGalleryClick: () -> Unit,
    onOpenFilmRoll: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentProfileIndex,
        pageCount = { uiState.profiles.size.coerceAtLeast(1) },
    )
    val currentProfile = uiState.profiles.getOrNull(uiState.currentProfileIndex)
    val lifecycleOwner = LocalLifecycleOwner.current

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen preview with zero padding
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = uiState.profiles.isNotEmpty(),
        ) { _ ->
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Chrome overlay (draws on top of full-screen preview)
        currentProfile?.let {
            ProfileChromeOverlay(profile = it, modifier = Modifier.fillMaxSize())
        }

        // Top controls
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

        // Capture area stays fixed at bottom center across state changes.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 48.dp),
        ) {
            CaptureArea(
                profile = currentProfile,
                cameraState = uiState.cameraState,
                onCapture = {
                    scope.launch { onCapture() }
                },
                onOpenFilmRoll = onOpenFilmRoll,
            )
        }
    }
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
) {
    val profileFont = profile?.fontFamily() ?: VintageCamTypography.digitalFont
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(cameraState) {
        if (cameraState == CameraState.Capturing) {
            showFlash = true
            delay(100)
            showFlash = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = profile?.displayName.orEmpty().uppercase(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = profileFont,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = when (cameraState) {
                CameraState.Capturing -> "BUSY"
                CameraState.Processing -> "PROC"
                CameraState.Previewing -> "READY"
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = profileFont,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Wrap button + flash in a Box so flash is scoped to button size
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            // The actual button
            CaptureButton(
                onClick = onCapture,
                onSwipeUp = onOpenFilmRoll,
                enabled = cameraState == CameraState.Previewing,
            )

            // Flash overlay scoped to button size only
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
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by androidx.compose.animation.core.animateFloatAsState(
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
            .pointerInput(onSwipeUp) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -20f) {
                        onSwipeUp()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = 4.dp, color = Color.White, shape = CircleShape),
        )

        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .background(Color.Black, CircleShape),
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
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Scanlines across full screen
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

            // Vignette gradient
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

        // Static red dot + "STBY" in top left
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(Color.Red)
            }
            Text(
                text = "STBY",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = VintageCamTypography.vhsFont,
            )
        }
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
        // Small green "READY" indicator in top corner
        Text(
            text = "READY",
            color = Color.Green,
            fontSize = 10.sp,
            fontFamily = VintageCamTypography.digitalFont,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )

        // Subtle gradient at bottom
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

private fun CameraProfile.fontFamily(): FontFamily {
    return when (era) {
        Era.EIGHTIES -> VintageCamTypography.vhsFont
        Era.NINETIES -> VintageCamTypography.filmFont
        Era.TWO_THOUSANDS -> VintageCamTypography.digitalFont
    }
}
