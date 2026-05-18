package com.vintagecam.app.ui.viewfinder

import android.Manifest
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onGalleryClick: () -> Unit,
    onOpenFilmRoll: () -> Unit,
    onBindCamera: (androidx.lifecycle.LifecycleOwner, androidx.camera.view.PreviewView) -> Unit,
) {
    val currentProfile = uiState.profiles.getOrNull(uiState.currentProfileIndex)
    val context = LocalContext.current
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

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Full-screen camera preview ──
        // Single FrameLayout holds both CameraX PreviewView (bottom) and
        // PreviewOverlayView (top) so effects render LIVE on the preview.
        AndroidView(
            factory = {
                (previewViews.root.parent as? ViewGroup)?.removeView(previewViews.root)
                previewViews.root
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                currentProfile?.let { previewViews.overlay.setProfile(it) }
            },
        )

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
                    onProfileSelected = onProfileSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )

                CaptureArea(
                    profile = currentProfile,
                    cameraState = uiState.cameraState,
                    onCapture = onCapture,
                    onOpenFilmRoll = onOpenFilmRoll,
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
