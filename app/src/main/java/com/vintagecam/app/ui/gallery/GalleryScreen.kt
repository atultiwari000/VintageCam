package com.vintagecam.app.ui.gallery

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.hilt.navigation.compose.hiltViewModel
import com.vintagecam.profiles.Era
import com.vintagecam.profiles.data.SavedPhoto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onClose: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        if (uiState.isFullScreen) {
            viewModel.closeFullScreen()
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .pointerInput(uiState.isFullScreen) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 60f) {
                        viewModel.closeFullScreen()
                    }
                }
            },
    ) {
        if (uiState.isFullScreen && uiState.fullScreenPhoto != null) {
            FullScreenViewer(
                uiState = uiState,
                onClose = viewModel::closeFullScreen,
                onPrevious = viewModel::navigatePrevious,
                onNext = viewModel::navigateNext,
                onDelete = viewModel::deleteCurrentPhoto,
            )
        } else {
            FilmstripGallery(
                photos = uiState.photos,
                onPhotoClick = viewModel::openFullScreen,
                onClose = onClose,
            )
        }
    }
}

// ── Filmstrip Gallery ──────────────────────────────────────────────────

@Composable
private fun FilmstripGallery(
    photos: List<SavedPhoto>,
    onPhotoClick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FILM ROLL",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${photos.size} frames",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close gallery",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (photos.isEmpty()) {
            EmptyGallery(onClose = onClose)
        } else {
            // Filmstrip — horizontal scrolling row of thumbnails
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                    FilmstripFrame(
                        photo = photo,
                        onClick = { onPhotoClick(index) },
                    )
                }
            }
        }
    }
}

// ── Single Filmstrip Frame ─────────────────────────────────────────────

@Composable
private fun FilmstripFrame(
    photo: SavedPhoto,
    onClick: () -> Unit,
) {
    var bitmap by remember(photo.id, photo.filePath, photo.isProcessing) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    // Load thumbnail on composition
    if (!photo.isProcessing && photo.errorMessage == null && bitmap == null) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
        }
        bitmap = BitmapFactory.decodeFile(photo.filePath, options)
    }

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Photo thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
                .border(
                    width = borderWidthFor(photo),
                    color = Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.isProcessing) {
                LoadingDevelopingIndicator()
            } else if (photo.errorMessage != null) {
                Text(
                    text = "FAILED",
                    color = Color(0xFFFF6B6B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Photo ${photo.profileName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Profile label
        Text(
            text = if (photo.isProcessing) "DEVELOPING" else photo.profileName.uppercase(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )

        // Date label
        Text(
            text = formatDate(photo.timestampMillis),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Full-Screen Viewer ─────────────────────────────────────────────────

@Composable
private fun FullScreenViewer(
    uiState: GalleryUiState,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDelete: () -> Unit,
) {
    val photo = uiState.fullScreenPhoto ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 100f) onPrevious()
                    else if (dragAmount < -100f) onNext()
                }
            },
    ) {
        // The photo image
        if (photo.isProcessing) {
            DevelopingFullScreen()
        } else if (photo.errorMessage != null) {
            FailedFullScreen(photo.errorMessage)
        } else if (uiState.fullScreenBitmap != null) {
            Image(
                bitmap = uiState.fullScreenBitmap!!.asImageBitmap(),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Loading placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
        }

        // Top bar — close, profile name, delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = photo.profileName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatDate(photo.timestampMillis),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            IconButton(
                onClick = onDelete,
                enabled = !uiState.deleting && !photo.isProcessing,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete photo",
                    tint = Color(0xFFFF4444),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Navigation arrows (left / right)
        if (uiState.photos.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Page indicator
        val currentIndex = uiState.photos.indexOfFirst { it.id == photo.id }
        if (uiState.photos.size > 1) {
            Text(
                text = "${currentIndex + 1} / ${uiState.photos.size}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            )
        }

        // Bottom metadata
        if (!photo.isProcessing && photo.errorMessage == null) {
            MetadataFooter(photo = photo)
        }
    }
}

@Composable
private fun LoadingDevelopingIndicator() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = Color.White.copy(alpha = 0.75f),
            strokeWidth = 2.dp,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "DEV",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DevelopingFullScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.14f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "DEVELOPING FRAME",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
private fun FailedFullScreen(message: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "FRAME FAILED",
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message ?: "Capture failed",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 10.sp,
            )
        }
    }
}

// ── Metadata Footer ────────────────────────────────────────────────────

@Composable
private fun MetadataFooter(photo: SavedPhoto) {
    // Positioned at the bottom by the parent Box in FullScreenViewer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            val file = File(photo.filePath)
            Text(
                text = photo.profileName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatDate(photo.timestampMillis),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "${file.length() / 1024} KB",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 8.sp,
            )
        }
    }
}

// ── Empty State ────────────────────────────────────────────────────────

@Composable
private fun EmptyGallery(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NO FRAMES YET",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Capture a photo to start your roll",
                color = Color.White.copy(alpha = 0.15f),
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Swipe down or tap   to return",
                color = Color.White.copy(alpha = 0.12f),
                fontSize = 9.sp,
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────

private fun borderWidthFor(photo: SavedPhoto) = when (photo.profileId) {
    "disposable_1998" -> 3.dp
    "vhs_1985" -> 2.dp
    "digicam_2003" -> 1.dp
    else -> 2.dp
}

private fun formatDate(millis: Long): String {
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(Date(millis))
}
