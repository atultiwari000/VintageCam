package com.vintagecam.app.ui.viewfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vintagecam.app.ui.theme.VintageCamTypography
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.Era
import com.vintagecam.profiles.ViewfinderType

@Composable
internal fun ProfileChromeOverlay(
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
