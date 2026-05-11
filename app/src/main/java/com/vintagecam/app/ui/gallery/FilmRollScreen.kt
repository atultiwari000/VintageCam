package com.vintagecam.app.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.Era
import com.vintagecam.profiles.data.CapturedPhoto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilmRollScreen(
    photos: List<CapturedPhoto>,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    // Force the pager to reinitialize when the photo count changes.
    // Without this, rememberPagerState captures the initial page count
    // and ignores subsequent additions while the screen is alive.
    key(photos.size) {
        val pagerState = rememberPagerState(
            pageCount = { photos.size.coerceAtLeast(1) },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(onClose) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 28f) onClose()
                    }
                },
        ) {
            if (photos.isEmpty()) {
                EmptyRollState(onClose = onClose)
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val photo = photos[page]
                    // Use a stable per-photo key for rememberSaveable.
                    // Fall back to index when URI is empty (failed save).
                    val saveableKey = if (photo.uri != android.net.Uri.EMPTY) {
                        "meta_${photo.uri}"
                    } else {
                        "meta_page_$page"
                    }
                    var showMetadata by rememberSaveable(saveableKey) {
                        mutableStateOf(false)
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = photo.bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(24.dp)
                                .border(
                                    width = borderWidthFor(photo.profile),
                                    color = Color.White,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .clip(RoundedCornerShape(2.dp))
                                .combinedClickable(
                                    onLongClick = { showMetadata = !showMetadata },
                                    onClick = {},
                                ),
                        )

                        if (showMetadata) {
                            MetadataOverlay(photo = photo)
                        }
                    }
                }
            }

            // Close button (always on top)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close film roll",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRollState(onClose: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        color = Color.Black,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No developed photos yet", color = Color.White, fontSize = 18.sp)
            Text("Capture a frame to start a roll", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text("Swipe down or tap back to return", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MetadataOverlay(photo: CapturedPhoto) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                photo.profile.displayName,
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = fontFamilyFor(photo.profile),
            )
            Text(
                formatTimestamp(photo.timestampMillis),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
            )
        }
    }
}

private fun borderWidthFor(profile: CameraProfile) = when (profile.era) {
    Era.NINETIES -> 12.dp
    Era.EIGHTIES -> 8.dp
    Era.TWO_THOUSANDS -> 4.dp
}

private fun fontFamilyFor(profile: CameraProfile): FontFamily = when (profile.era) {
    Era.EIGHTIES -> FontFamily.Monospace
    Era.NINETIES -> FontFamily.Serif
    Era.TWO_THOUSANDS -> FontFamily.SansSerif
}

private fun formatTimestamp(millis: Long): String {
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(Date(millis))
}
