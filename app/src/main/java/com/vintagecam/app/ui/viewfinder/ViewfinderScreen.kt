package com.vintagecam.app.ui.viewfinder

import android.Manifest
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.vintagecam.camera.pipeline.PreviewOverlayView
import com.vintagecam.profiles.CameraProfile

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

    // Clean up camera when navigating away
    DisposableEffect(lifecycleOwner) {
        onDispose {
            android.util.Log.d("ViewfinderScreen", "onDispose: stopping preview")
            viewModel.onStopPreview()
        }
    }

    when (cameraPermissionState.status) {
        is PermissionStatus.Denied -> PermissionDeniedContent()
        PermissionStatus.Granted -> {
            ViewfinderContent(
                uiState = uiState,
                lifecycleOwner = lifecycleOwner,
                onSwitchCamera = viewModel::onSwitchCamera,
                onCapture = viewModel::onCapture,
                onToggleFlash = viewModel::onToggleFlash,
                onProfileSelected = viewModel::onProfileSelected,
                onFocusTap = viewModel::onFocusTap,
                onGalleryClick = onOpenFilmRoll,
                onOpenFilmRoll = onOpenFilmRoll,
                onBindCamera = viewModel::bindCamera,
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

@Composable
private fun ViewfinderContent(
    uiState: ViewfinderUiState,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onSwitchCamera: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onProfileSelected: (Int) -> Unit,
    onFocusTap: (PreviewView, Float, Float) -> Unit,
    onGalleryClick: () -> Unit,
    onOpenFilmRoll: () -> Unit,
    onBindCamera: (androidx.lifecycle.LifecycleOwner, androidx.camera.view.PreviewView) -> Unit,
) {
    val currentProfile = uiState.profiles.getOrNull(uiState.currentProfileIndex)
    val context = LocalContext.current
    var filterBrowserExpanded by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val previewViews = remember(context) {
        val previewView = PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_START
        }
        val overlayView = PreviewOverlayView(context).apply {
            setLayerType(ViewGroup.LAYER_TYPE_HARDWARE, null)
        }
        val root = FrameLayout(context).apply {
            addView(previewView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(overlayView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }

        PreviewViews(
            root = root,
            preview = previewView,
            overlay = overlayView,
        )
    }

    LaunchedEffect(lifecycleOwner, previewViews.preview) {
        onBindCamera(lifecycleOwner, previewViews.preview)
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            kotlinx.coroutines.delay(850)
            focusPoint = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Full-screen camera preview ──
        // Single FrameLayout holds both CameraX PreviewView (bottom) and
        // PreviewOverlayView (top) so effects render LIVE on the preview.
        AndroidView(
            factory = {
                (previewViews.root.parent as? ViewGroup)?.removeView(previewViews.root)
                previewViews.root
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(previewViews.preview) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        onFocusTap(previewViews.preview, offset.x, offset.y)
                    }
                },
            update = {
                currentProfile?.let { previewViews.overlay.setProfile(it) }
            },
        )

        focusPoint?.let { point ->
            FocusReticle(point = point, modifier = Modifier.fillMaxSize())
        }

        // ── Chrome overlay (bezel / viewfinder frame) ──
        currentProfile?.let {
            ProfileChromeOverlay(profile = it, modifier = Modifier.fillMaxSize())
        }

        // ── Top controls ──
        TopControlsRow(
            flashEnabled = uiState.flashEnabled,
            controlsEnabled = uiState.cameraState == CameraState.Previewing,
            onToggleFlash = onToggleFlash,
            onSwitchCamera = onSwitchCamera,
            onGalleryClick = onGalleryClick,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )

        // ── Bottom area: Snapchat-style filter carousel + capture button ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileFilterCarousel(
                    profiles = uiState.profiles,
                    selectedIndex = uiState.currentProfileIndex,
                    expanded = filterBrowserExpanded,
                    onExpandedChange = { filterBrowserExpanded = it },
                    onProfileSelected = { index ->
                        onProfileSelected(index)
                        filterBrowserExpanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )

                CaptureArea(
                    profile = currentProfile,
                    cameraState = uiState.cameraState,
                    developingCount = uiState.developingCount,
                    onCapture = onCapture,
                    onOpenFilmRoll = onOpenFilmRoll,
                    showProfileLabel = filterBrowserExpanded,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}

private data class PreviewViews(
    val root: FrameLayout,
    val preview: PreviewView,
    val overlay: PreviewOverlayView,
)

@Composable
private fun FocusReticle(
    point: Offset,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = 32.dp.toPx()
        val tick = 12.dp.toPx()
        val stroke = 2.dp.toPx()
        val color = Color.White.copy(alpha = 0.86f)
        drawCircle(color = color, radius = radius, center = point, style = Stroke(stroke))
        drawLine(color, Offset(point.x - radius - tick, point.y), Offset(point.x - radius + tick, point.y), stroke)
        drawLine(color, Offset(point.x + radius - tick, point.y), Offset(point.x + radius + tick, point.y), stroke)
        drawLine(color, Offset(point.x, point.y - radius - tick), Offset(point.x, point.y - radius + tick), stroke)
        drawLine(color, Offset(point.x, point.y + radius - tick), Offset(point.x, point.y + radius + tick), stroke)
    }
}
