package com.vintagecam.app.ui.viewfinder

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.vintagecam.profiles.ViewfinderType
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ViewfinderScreen(viewModel: ViewfinderViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
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
                onStartPreview = { previewView ->
                    viewModel.onStartPreview(lifecycleOwner, previewView)
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
    onStartPreview: (PreviewView) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedProfileIndex,
        pageCount = { uiState.profiles.size.coerceAtLeast(1) },
    )

    LaunchedEffect(pagerState.currentPage) {
        if (uiState.profiles.isNotEmpty()) {
            onProfilePageChanged(pagerState.currentPage)
        }
    }

    LaunchedEffect(uiState.selectedProfileIndex) {
        if (pagerState.currentPage != uiState.selectedProfileIndex) {
            pagerState.animateScrollToPage(uiState.selectedProfileIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = uiState.profiles.isNotEmpty(),
        ) { _ ->
            CameraPreview(
                context = context,
                onStartPreview = onStartPreview,
            )
        }

        val currentProfile = uiState.profiles.getOrNull(uiState.selectedProfileIndex)
        currentProfile?.let {
            ProfileChromeOverlay(viewfinderType = it.viewfinderType)
        }

        TopBar(
            flashEnabled = uiState.flashEnabled,
            onToggleFlash = onToggleFlash,
            onSwitchCamera = onSwitchCamera,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
        )

        BottomCaptureControls(
            profileName = currentProfile?.displayName.orEmpty(),
            cameraState = uiState.cameraState,
            onCapture = {
                scope.launch { onCapture() }
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
        )
    }
}

@Composable
private fun CameraPreview(
    context: Context,
    onStartPreview: (PreviewView) -> Unit,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        factory = {
            PreviewView(context).also { view ->
                previewView = view
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    LaunchedEffect(previewView) {
        previewView?.let(onStartPreview)
    }
}

@Composable
private fun TopBar(
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleFlash) {
            Text(
                text = if (flashEnabled) "FLASH ON" else "FLASH OFF",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        IconButton(onClick = onSwitchCamera) {
            Text(
                text = "SWITCH",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BottomCaptureControls(
    profileName: String,
    cameraState: CameraState,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = profileName.uppercase(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = cameraState == CameraState.Previewing) { onCapture() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
            )
        }

        Text(
            text = when (cameraState) {
                CameraState.Previewing -> "READY"
                CameraState.Capturing -> "CAPTURING"
                CameraState.Processing -> "PROCESSING"
            },
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ProfileChromeOverlay(viewfinderType: ViewfinderType) {
    when (viewfinderType) {
        ViewfinderType.CRT -> VhsOverlay()
        ViewfinderType.OPTICAL -> DisposableOverlay()
        ViewfinderType.LCD -> DigicamOverlay()
    }
}

@Composable
private fun VhsOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanlineSpacing = 6f
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.12f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += scanlineSpacing
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(Color.Red)
            }
            Text(text = "REC", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DisposableOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
                .width(64.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp),
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = "24",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFFFFF17A),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DigicamOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.65f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("1/60", color = Color.White, fontSize = 11.sp)
                Text("BAT 82%", color = Color.White, fontSize = 11.sp)
                Text("AUTO", color = Color.White, fontSize = 11.sp)
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
